package com.shareef.videoplayersj.desktop.torrent

import com.shareef.videoplayersj.desktop.data.AppDirs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import kotlin.concurrent.thread

/**
 * Keeps one copy of the app running. Clicking a magnet link launches the app again with the link
 * as an argument; that second copy hands the link to the running one over a loopback socket and
 * quits, so the link opens in the window that's already up.
 */
object SingleInstance {

    // Buffered until the window is up to collect them: macOS hands over a clicked link at launch.
    private val channel = Channel<String>(Channel.UNLIMITED)

    /** Magnet links (or an empty string, meaning "just come to the front") from other launches. */
    val messages: Flow<String> = channel.receiveAsFlow()

    /** Hands a message to this copy directly, as when the OS delivers a clicked link as an event. */
    fun deliver(message: String) {
        channel.trySend(message)
    }

    @Suppress("unused") // Held for the life of the process; releasing it would let a second copy start.
    private var lock: FileLock? = null

    /** Returns false if another copy is running and has been handed [args]; the caller should exit. */
    fun claim(args: List<String>): Boolean {
        val lockFile = File(AppDirs.dataDir, "instance.lock")
        val portFile = File(AppDirs.dataDir, "instance.port")
        val channel = runCatching { RandomAccessFile(lockFile, "rw").channel }.getOrNull() ?: return true
        val acquired = runCatching { channel.tryLock() }.getOrNull()
        if (acquired == null) {
            if (forward(portFile, args)) return false
            // The other copy isn't answering; better to run twice than not at all.
            return true
        }
        lock = acquired
        listen(portFile)
        return true
    }

    private fun forward(portFile: File, args: List<String>): Boolean = runCatching {
        val port = portFile.readText().trim().toInt()
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.soTimeout = 3000
            val writer = socket.getOutputStream().bufferedWriter()
            (args.ifEmpty { listOf("") }).forEach { writer.write(it.replace('\n', ' ')); writer.newLine() }
            writer.flush()
        }
        true
    }.getOrDefault(false)

    private fun listen(portFile: File) {
        val server = runCatching { ServerSocket(0, 10, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        runCatching { portFile.writeText(server.localPort.toString()) }
        thread(isDaemon = true, name = "single-instance") {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    socket.use { s ->
                        s.soTimeout = 3000
                        s.getInputStream().bufferedReader().lineSequence().forEach { channel.trySend(it.trim()) }
                    }
                }
            }
        }
    }
}
