package com.shareef.videoplayersj.transfer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

const val DEFAULT_DISCOVERY_PORT = 47231

private const val ANNOUNCE_PREFIX = "VPSJ1 "
private const val ANNOUNCE_INTERVAL_MS = 2_000L
private const val PEER_TIMEOUT_MS = 7_000L
private const val ACCEPT_TIMEOUT_MS = 120_000L
private const val CONNECT_TIMEOUT_MS = 5_000
private const val IO_TIMEOUT_MS = 30_000
private const val PROGRESS_INTERVAL_MS = 150L
private const val BUFFER_SIZE = 256 * 1024
private const val MAX_LINE_BYTES = 1 shl 20

/**
 * Sends files between copies of VideoPlayerSJ on the same network.
 *
 * Each copy announces itself by UDP broadcast every couple of seconds and listens for the others,
 * so devices show up without any pairing. Files go over a direct TCP connection: the sender
 * describes what it's sending, the receiving user accepts and picks a folder, then the files
 * stream across one after another. Nothing is received without being accepted.
 */
class LocalTransfer(
    private val deviceId: String,
    private val deviceName: String,
    private val platform: String,
    private val scope: CoroutineScope,
    private val discoveryPort: Int = DEFAULT_DISCOVERY_PORT,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _offers = MutableStateFlow<List<IncomingOffer>>(emptyList())
    /** Offers waiting for an answer, oldest first. */
    val offers: StateFlow<List<IncomingOffer>> = _offers.asStateFlow()

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    /** Transfers in both directions, newest first. */
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    private val _received = MutableSharedFlow<ReceivedFiles>(extraBufferCapacity = 16)
    val received: SharedFlow<ReceivedFiles> = _received.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val pendingAnswers = ConcurrentHashMap<String, CompletableDeferred<File?>>()
    private val openSockets = ConcurrentHashMap<String, Socket>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    private var server: ServerSocket? = null
    private var udp: DatagramSocket? = null
    private var jobs: List<Job> = emptyList()

    /** Starts listening for files and announcing this device. Safe to call repeatedly. */
    @Synchronized
    fun start() {
        if (server != null) return
        val tcp = runCatching { ServerSocket(0) }.getOrNull() ?: return
        val datagram = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(discoveryPort))
            }
        }.getOrNull()
        server = tcp
        udp = datagram
        _isRunning.value = true
        jobs = listOfNotNull(
            scope.launch(Dispatchers.IO) { acceptLoop(tcp) },
            datagram?.let { scope.launch(Dispatchers.IO) { listenLoop(it) } },
            datagram?.let { scope.launch(Dispatchers.IO) { announceLoop(it, tcp.localPort) } },
        )
    }

    /** Stops being visible and accepting new transfers. Transfers already under way carry on. */
    @Synchronized
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        runCatching { server?.close() }
        runCatching { udp?.close() }
        server = null
        udp = null
        _isRunning.value = false
        _peers.value = emptyList()
        // Offers already showing stay answerable: on Android the app stops while the user is
        // off granting storage access, and that mustn't decline what they were about to accept.
    }

    /** The port this device accepts files on, or null when stopped. */
    val port: Int? get() = server?.localPort

    fun send(peer: Peer, files: List<OutgoingFile>) {
        if (files.isEmpty()) return
        val id = UUID.randomUUID().toString()
        val total = files.sumOf { it.size }
        upsert(
            Transfer(
                id = id,
                direction = TransferDirection.Sending,
                peerName = peer.name,
                fileCount = files.size,
                currentFile = null,
                bytesDone = 0,
                totalBytes = total,
                rate = 0,
                state = TransferState.WaitingForAccept,
            ),
        )
        scope.launch(Dispatchers.IO) { runSend(id, peer, files) }
    }

    fun accept(offerId: String, dir: File) {
        pendingAnswers[offerId]?.complete(dir)
    }

    fun decline(offerId: String) {
        pendingAnswers[offerId]?.complete(null)
    }

    fun cancel(transferId: String) {
        cancelled += transferId
        openSockets[transferId]?.let { runCatching { it.close() } }
    }

    fun clearFinished() {
        _transfers.update { list -> list.filter { it.isActive } }
    }

    // --- Discovery ---------------------------------------------------------------------------

    private fun announceLoop(socket: DatagramSocket, tcpPort: Int) {
        val message = (ANNOUNCE_PREFIX + json.encodeToString(Announcement.serializer(), Announcement(deviceId, deviceName, platform, tcpPort)))
            .toByteArray(Charsets.UTF_8)
        while (!socket.isClosed) {
            for (address in broadcastAddresses()) {
                runCatching { socket.send(DatagramPacket(message, message.size, address, discoveryPort)) }
            }
            val now = System.currentTimeMillis()
            _peers.update { peers -> peers.filter { now - it.lastSeen < PEER_TIMEOUT_MS } }
            try {
                Thread.sleep(ANNOUNCE_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun listenLoop(socket: DatagramSocket) {
        val buffer = ByteArray(2048)
        while (!socket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: IOException) {
                return
            }
            val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
            if (!text.startsWith(ANNOUNCE_PREFIX)) continue
            val announcement = runCatching {
                json.decodeFromString(Announcement.serializer(), text.removePrefix(ANNOUNCE_PREFIX))
            }.getOrNull() ?: continue
            if (announcement.id == deviceId) continue
            val peer = Peer(
                id = announcement.id,
                name = announcement.name,
                platform = announcement.platform,
                host = packet.address.hostAddress,
                port = announcement.port,
                lastSeen = System.currentTimeMillis(),
            )
            val isNew = _peers.value.none { it.id == peer.id }
            _peers.update { peers -> (peers.filter { it.id != peer.id } + peer).sortedBy { it.name.lowercase() } }
            // Answer a newcomer straight away, so both sides see each other within a moment.
            if (isNew) {
                val reply = (ANNOUNCE_PREFIX + json.encodeToString(Announcement.serializer(), Announcement(deviceId, deviceName, platform, server?.localPort ?: continue)))
                    .toByteArray(Charsets.UTF_8)
                runCatching { socket.send(DatagramPacket(reply, reply.size, packet.address, discoveryPort)) }
            }
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = mutableSetOf<InetAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .filter { it.address is Inet4Address }
                .mapNotNullTo(addresses) { it.broadcast }
        }
        runCatching { addresses += InetAddress.getByName("255.255.255.255") }
        return addresses.toList()
    }

    // --- Sending -----------------------------------------------------------------------------

    private suspend fun runSend(id: String, peer: Peer, files: List<OutgoingFile>) {
        val socket = Socket()
        openSockets[id] = socket
        try {
            socket.connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS)
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream().buffered(BUFFER_SIZE)
            val offer = OfferMessage(deviceId, deviceName, platform, files.map { FileInfo(it.name, it.size) })
            writeLine(output, json.encodeToString(OfferMessage.serializer(), offer))

            // Waits for the person on the other end to answer.
            socket.soTimeout = (ACCEPT_TIMEOUT_MS + 10_000).toInt()
            val reply = json.decodeFromString(ReplyMessage.serializer(), readLine(input))
            if (!reply.accepted) {
                update(id) { it.copy(state = TransferState.Declined, error = reply.reason) }
                return
            }
            socket.soTimeout = IO_TIMEOUT_MS
            update(id) { it.copy(state = TransferState.Running) }

            val progress = Progress(id)
            for (file in files) {
                update(id) { it.copy(currentFile = file.name) }
                file.open().use { source -> copyExactly(source, output, file.size, progress) }
            }
            output.flush()
            progress.flush()
            val done = json.decodeFromString(DoneMessage.serializer(), readLine(input))
            update(id) { it.copy(state = if (done.ok) TransferState.Done else TransferState.Failed, currentFile = null) }
        } catch (e: Exception) {
            fail(id, e)
        } finally {
            openSockets.remove(id)
            runCatching { socket.close() }
        }
    }

    // --- Receiving ---------------------------------------------------------------------------

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val socket = try {
                server.accept()
            } catch (_: IOException) {
                return
            }
            scope.launch(Dispatchers.IO) { runReceive(socket) }
        }
    }

    private suspend fun runReceive(socket: Socket) {
        val id = UUID.randomUUID().toString()
        openSockets[id] = socket
        val written = mutableListOf<File>()
        var finished = false
        try {
            socket.soTimeout = IO_TIMEOUT_MS
            val input = BufferedInputStream(socket.getInputStream(), BUFFER_SIZE)
            val output = socket.getOutputStream().buffered()
            val offer = json.decodeFromString(OfferMessage.serializer(), readLine(input))
            if (offer.files.isEmpty() || offer.files.any { it.size < 0 }) return

            val incoming = IncomingOffer(id, offer.senderName, offer.senderPlatform, offer.files)
            val answer = CompletableDeferred<File?>()
            pendingAnswers[id] = answer
            _offers.update { it + incoming }
            val dir = try {
                withTimeoutOrNull(ACCEPT_TIMEOUT_MS) { answer.await() }
            } finally {
                pendingAnswers.remove(id)
                _offers.update { offers -> offers.filter { it.id != id } }
            }

            if (dir == null) {
                writeLine(output, json.encodeToString(ReplyMessage.serializer(), ReplyMessage(false)))
                return
            }
            val transfer = Transfer(
                id = id,
                direction = TransferDirection.Receiving,
                peerName = offer.senderName,
                fileCount = offer.files.size,
                currentFile = null,
                bytesDone = 0,
                totalBytes = incoming.totalBytes,
                rate = 0,
                state = TransferState.Running,
                savedTo = dir.absolutePath,
            )
            upsert(transfer)
            val problem = when {
                !dir.isDirectory && !dir.mkdirs() -> "Can't create ${dir.path}"
                !dir.canWrite() -> "Can't write to ${dir.path}"
                dir.usableSpace in 1 until incoming.totalBytes -> "Not enough free space in ${dir.path}"
                else -> null
            }
            if (problem != null) {
                writeLine(output, json.encodeToString(ReplyMessage.serializer(), ReplyMessage(false, problem)))
                update(id) { it.copy(state = TransferState.Failed, error = problem) }
                return
            }
            writeLine(output, json.encodeToString(ReplyMessage.serializer(), ReplyMessage(true)))

            val progress = Progress(id)
            for (info in offer.files) {
                val target = uniqueFile(dir, safeFileName(info.name))
                update(id) { it.copy(currentFile = target.name) }
                written += target
                target.outputStream().buffered(BUFFER_SIZE).use { sink -> copyExactly(input, sink, info.size, progress) }
            }
            progress.flush()
            finished = true
            writeLine(output, json.encodeToString(DoneMessage.serializer(), DoneMessage(true)))
            update(id) { it.copy(state = TransferState.Done, currentFile = null) }
            _received.tryEmit(ReceivedFiles(dir.absolutePath, written.map { it.name }))
        } catch (e: Exception) {
            if (_transfers.value.any { it.id == id }) fail(id, e)
        } finally {
            // Half-received files are useless; don't leave them looking like real ones.
            if (!finished) written.forEach { it.delete() }
            openSockets.remove(id)
            runCatching { socket.close() }
        }
    }

    // --- Plumbing ----------------------------------------------------------------------------

    private inner class Progress(private val id: String) {
        private val startedAt = System.currentTimeMillis()
        private var done = 0L
        private var lastPublished = 0L

        fun add(bytes: Int) {
            done += bytes
            val now = System.currentTimeMillis()
            if (now - lastPublished >= PROGRESS_INTERVAL_MS) {
                lastPublished = now
                publish(now)
            }
        }

        fun flush() = publish(System.currentTimeMillis())

        private fun publish(now: Long) {
            val elapsed = (now - startedAt).coerceAtLeast(1)
            update(id) { it.copy(bytesDone = done, rate = done * 1000 / elapsed) }
        }
    }

    private fun copyExactly(source: InputStream, sink: OutputStream, size: Long, progress: Progress) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = size
        while (remaining > 0) {
            val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("Connection closed before the file finished")
            sink.write(buffer, 0, read)
            remaining -= read
            progress.add(read)
        }
    }

    private fun fail(id: String, e: Exception) {
        val state = if (id in cancelled) TransferState.Cancelled else TransferState.Failed
        cancelled -= id
        update(id) {
            it.copy(
                state = state,
                currentFile = null,
                error = if (state == TransferState.Cancelled) null else (e.message ?: e.javaClass.simpleName),
            )
        }
    }

    private fun upsert(transfer: Transfer) {
        _transfers.update { list -> listOf(transfer) + list.filter { it.id != transfer.id } }
    }

    private fun update(id: String, transform: (Transfer) -> Transfer) {
        _transfers.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun writeLine(output: OutputStream, line: String) {
        output.write((line + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }

    /** Reads up to a newline without buffering past it, so file bytes that follow stay in [input]. */
    private fun readLine(input: InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("Connection closed")
            if (b == '\n'.code) break
            bytes.write(b)
            if (bytes.size() > MAX_LINE_BYTES) throw IOException("Message too long")
        }
        return bytes.toString(Charsets.UTF_8)
    }

    companion object {
        /** A plain file name from whatever the sender called it: no folders, no characters Windows rejects. */
        fun safeFileName(name: String): String {
            val base = name.substringAfterLast('/').substringAfterLast('\\')
                .filter { it >= ' ' && it !in "<>:\"|?*" }
                .trim()
                .trimStart('.')
                .take(200)
            return base.ifEmpty { "file" }
        }

        /** [name] in [dir], or "name (2).ext" and so on if that's taken. */
        fun uniqueFile(dir: File, name: String): File {
            var candidate = File(dir, name)
            if (!candidate.exists()) return candidate
            val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
            val stem = name.substring(0, dot)
            val ext = name.substring(dot)
            var n = 2
            while (candidate.exists()) candidate = File(dir, "$stem ($n)$ext").also { n++ }
            return candidate
        }
    }
}
