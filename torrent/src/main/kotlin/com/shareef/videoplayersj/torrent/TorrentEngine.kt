package com.shareef.videoplayersj.torrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.add_torrent_params
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.session_handle
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private const val POLL_INTERVAL_MS = 1_000L
private const val RESUME_SAVE_EVERY_POLLS = 30
private const val STOP_RESUME_WAIT_MS = 5_000L
private const val FINISHED_HISTORY_LIMIT = 50

/**
 * A leech-only torrent client: downloads from magnet links and, the moment a download is
 * complete, drops the torrent and keeps the files. Nothing is seeded afterwards.
 *
 * Downloads survive restarts: each one's magnet link and save folder are kept in [stateDir],
 * along with libtorrent's resume data, so a restarted download picks up where it left off
 * without fetching the file list again.
 *
 * libtorrent is only loaded on [start], so an app that never downloads anything never pays for it.
 */
class TorrentEngine(
    private val stateDir: File,
    private val scope: CoroutineScope,
    /** Runs once before libtorrent is first loaded, e.g. to point it at its native library. */
    private val beforeStart: () -> Unit = {},
) {

    private val stateFile = File(stateDir, "torrents.json")
    private val resumeDir = File(stateDir, "resume")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // Guarded by `this`: touched from callers, the poll loop and libtorrent's alert thread.
    private val entries = LinkedHashMap<String, ActiveEntry>()
    private var finishedList: List<FinishedDownload>
    private var session: SessionManager? = null
    private var pollJob: Job? = null
    private val pendingResumeSaves = AtomicInteger(0)

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _finished = MutableStateFlow<List<FinishedDownload>>(emptyList())
    val finished: StateFlow<List<FinishedDownload>> = _finished.asStateFlow()

    private val _completions = MutableSharedFlow<FinishedDownload>(extraBufferCapacity = 16)
    /** Emits each download as it completes, e.g. to rescan the library or post a notification. */
    val completions: SharedFlow<FinishedDownload> = _completions.asSharedFlow()

    private val _startError = MutableStateFlow<String?>(null)
    /** Set when libtorrent couldn't be loaded on this device. */
    val startError: StateFlow<String?> = _startError.asStateFlow()

    init {
        stateDir.mkdirs()
        val state = runCatching { json.decodeFromString<TorrentState>(stateFile.readText()) }.getOrDefault(TorrentState())
        state.active.forEach { entries[it.id] = it }
        finishedList = state.finished
        _finished.value = finishedList
        _downloads.value = state.active.map { placeholderItem(it) }
    }

    val hasActiveDownloads: Boolean get() = synchronized(this) { entries.isNotEmpty() }

    val isRunning: Boolean get() = synchronized(this) { session != null }

    /** Loads libtorrent and resumes any saved downloads. Safe to call repeatedly. */
    fun start(): Boolean {
        synchronized(this) {
            if (session != null) return true
            val newSession = try {
                beforeStart()
                val settings = SettingsPack().activeDownloads(4)
                SessionManager(false).apply {
                    addListener(alertListener)
                    start(SessionParams(settings))
                }
            } catch (e: Throwable) {
                // UnsatisfiedLinkError and friends: no libtorrent build for this device.
                _startError.value = e.message ?: e.toString()
                return false
            }
            session = newSession
            _startError.value = null
            entries.values.forEach { restore(newSession, it) }
            pollJob = scope.launch(Dispatchers.IO) { pollLoop() }
            return true
        }
    }

    /**
     * Starts downloading [magnet] into [saveDir], starting the engine if needed. Adding a torrent
     * that's already downloading just returns its id.
     */
    fun add(magnet: String, saveDir: File): Result<String> {
        val params = try {
            AddTorrentParams.parseMagnetUri(magnet.trim())
        } catch (e: Throwable) {
            return Result.failure(IllegalArgumentException("That isn't a valid magnet link."))
        }
        val id = params.infoHashes.best.toHex()
        if (!start()) return Result.failure(IllegalStateException("The downloader couldn't start: ${_startError.value}"))
        if (!saveDir.isDirectory && !saveDir.mkdirs()) {
            return Result.failure(IllegalArgumentException("Can't create ${saveDir.path}"))
        }
        synchronized(this) {
            if (id in entries) return Result.success(id)
            val entry = ActiveEntry(
                id = id,
                magnet = magnet.trim(),
                savePath = saveDir.absolutePath,
                name = Magnet.displayName(magnet) ?: params.name?.takeIf { it.isNotBlank() && it != id } ?: "Fetching name…",
                addedAt = System.currentTimeMillis(),
            )
            entries[id] = entry
            persist()
            session?.let { restore(it, entry) }
            publishPlaceholders()
        }
        return Result.success(id)
    }

    fun pause(id: String) {
        synchronized(this) {
            val entry = entries[id] ?: return
            entries[id] = entry.copy(paused = true)
            persist()
        }
        handle(id)?.let {
            it.unsetFlags(TorrentFlags.AUTO_MANAGED)
            it.pause()
            requestResumeData(it)
        }
    }

    fun resume(id: String) {
        synchronized(this) {
            val entry = entries[id] ?: return
            entries[id] = entry.copy(paused = false)
            persist()
        }
        handle(id)?.let {
            // A disk error (folder removed, permission revoked) pauses the torrent with the error set.
            it.swig().clear_error()
            it.setFlags(TorrentFlags.AUTO_MANAGED)
            it.resume()
        }
    }

    /** Stops and forgets a download, optionally deleting what was downloaded so far. */
    fun cancel(id: String, deleteFiles: Boolean) {
        synchronized(this) {
            entries.remove(id) ?: return
            persist()
        }
        resumeFile(id).delete()
        val s = synchronized(this) { session }
        handle(id)?.let { s?.remove(it, if (deleteFiles) session_handle.delete_files else session_handle.delete_partfile) }
        publishPlaceholders()
    }

    fun removeFinished(id: String) {
        synchronized(this) {
            finishedList = finishedList.filter { it.id != id }
            _finished.value = finishedList
            persist()
        }
    }

    fun clearFinished() {
        synchronized(this) {
            finishedList = emptyList()
            _finished.value = finishedList
            persist()
        }
    }

    /** Saves resume data for every download, then shuts libtorrent down. */
    fun stop() {
        val s = synchronized(this) {
            pollJob?.cancel()
            pollJob = null
            session.also { session = null }
        } ?: return
        val handles = synchronized(this) { entries.keys.toList() }.mapNotNull { find(s, it) }
        pendingResumeSaves.set(handles.size)
        handles.forEach { it.saveResumeData(TorrentHandle.SAVE_INFO_DICT) }
        val deadline = System.currentTimeMillis() + STOP_RESUME_WAIT_MS
        while (pendingResumeSaves.get() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        s.removeListener(alertListener)
        s.stop()
        publishPlaceholders()
    }

    // --- libtorrent plumbing ---------------------------------------------------------------

    private fun restore(s: SessionManager, entry: ActiveEntry) {
        val params = loadResumeData(entry.id) ?: runCatching { AddTorrentParams.parseMagnetUri(entry.magnet).swig() }.getOrNull() ?: return
        params.save_path = entry.savePath
        params.flags = if (entry.paused) {
            params.flags.and_(TorrentFlags.AUTO_MANAGED.inv()).or_(TorrentFlags.PAUSED)
        } else {
            params.flags.or_(TorrentFlags.AUTO_MANAGED)
        }
        s.swig().async_add_torrent(params)
    }

    private fun loadResumeData(id: String): add_torrent_params? {
        val file = resumeFile(id)
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val ec = error_code()
        val params = libtorrent.read_resume_data_ex(Vectors.bytes2byte_vector(bytes), ec)
        return if (ec.value() == 0) params else null
    }

    private fun resumeFile(id: String) = File(resumeDir, "$id.resume")

    private fun requestResumeData(handle: TorrentHandle) {
        if (handle.isValid) handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
    }

    private fun handle(id: String): TorrentHandle? {
        val s = synchronized(this) { session } ?: return null
        return find(s, id)
    }

    private fun find(s: SessionManager, id: String): TorrentHandle? =
        s.find(Sha1Hash.parseHex(id))?.takeIf { it.isValid }

    private val alertListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.SAVE_RESUME_DATA.swig(),
            AlertType.SAVE_RESUME_DATA_FAILED.swig(),
            AlertType.METADATA_RECEIVED.swig(),
        )

        override fun alert(alert: Alert<*>) {
            when (alert) {
                is TorrentFinishedAlert -> onFinished(alert.handle())
                is SaveResumeDataAlert -> {
                    onResumeData(alert)
                    pendingResumeSaves.decrementAndGet()
                }
                is MetadataReceivedAlert -> requestResumeData(alert.handle())
                else -> if (alert.type() == AlertType.SAVE_RESUME_DATA_FAILED) pendingResumeSaves.decrementAndGet()
            }
        }
    }

    private fun onResumeData(alert: SaveResumeDataAlert) {
        val params = alert.params()
        val id = params.infoHashes.best.toHex()
        // A late save for a torrent that has since finished or been cancelled must not bring it back.
        if (synchronized(this) { id !in entries }) return
        val bytes = AddTorrentParams.writeResumeDataBuf(params)
        resumeDir.mkdirs()
        val tmp = File(resumeDir, "$id.resume.tmp")
        runCatching {
            tmp.writeBytes(bytes)
            val target = resumeFile(id)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }

    private fun onFinished(handle: TorrentHandle) {
        if (!handle.isValid) return
        val status = handle.status()
        val id = status.infoHashes.best.toHex()
        val entry = synchronized(this) { entries.remove(id) } ?: return
        val done = FinishedDownload(
            id = id,
            name = status.name()?.takeIf { it.isNotBlank() } ?: entry.name,
            savePath = entry.savePath,
            sizeBytes = status.totalWanted(),
            finishedAt = System.currentTimeMillis(),
        )
        synchronized(this) {
            finishedList = (listOf(done) + finishedList.filter { it.id != id }).take(FINISHED_HISTORY_LIMIT)
            _finished.value = finishedList
            persist()
            session?.remove(handle)
        }
        resumeFile(id).delete()
        publishPlaceholders()
        _completions.tryEmit(done)
    }

    private suspend fun pollLoop() {
        var polls = 0
        while (true) {
            val s = synchronized(this) { session } ?: return
            refresh(s, saveResume = ++polls % RESUME_SAVE_EVERY_POLLS == 0)
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun refresh(s: SessionManager, saveResume: Boolean) {
        val snapshot = synchronized(this) { entries.values.toList() }
        var renamed = false
        val items = snapshot.map { entry ->
            val handle = find(s, entry.id) ?: return@map placeholderItem(entry)
            val status = handle.status()
            if (saveResume && handle.needSaveResumeData()) requestResumeData(handle)
            val name = status.name()?.takeIf { status.hasMetadata() && it.isNotBlank() }
            if (name != null && name != entry.name) {
                synchronized(this) { entries[entry.id]?.let { entries[entry.id] = it.copy(name = name) } }
                renamed = true
            }
            status.toItem(entry.copy(name = name ?: entry.name))
        }
        if (renamed) synchronized(this) { persist() }
        // A download may have finished or been cancelled while this snapshot was taken.
        val current = synchronized(this) { entries.keys.toSet() }
        _downloads.value = items.filter { it.id in current }
    }

    private fun TorrentStatus.toItem(entry: ActiveEntry): DownloadItem {
        val flags = flags()
        val paused = flags.and_(TorrentFlags.PAUSED).non_zero()
        val autoManaged = flags.and_(TorrentFlags.AUTO_MANAGED).non_zero()
        val error = errorCode().takeIf { it.isError }?.message
        val state = when {
            error != null -> DownloadState.Error
            paused && !autoManaged -> DownloadState.Paused
            paused -> DownloadState.Queued
            state() == TorrentStatus.State.DOWNLOADING_METADATA -> DownloadState.FetchingInfo
            state() == TorrentStatus.State.CHECKING_FILES || state() == TorrentStatus.State.CHECKING_RESUME_DATA -> DownloadState.Checking
            else -> DownloadState.Downloading
        }
        val remaining = totalWanted() - totalWantedDone()
        val rate = downloadPayloadRate()
        return DownloadItem(
            id = entry.id,
            name = entry.name,
            savePath = entry.savePath,
            state = state,
            progress = if (hasMetadata()) progress() else 0f,
            downloadedBytes = totalWantedDone(),
            totalBytes = if (hasMetadata()) totalWanted() else 0L,
            downloadRate = rate,
            uploadRate = uploadPayloadRate(),
            peers = numPeers(),
            seeds = numSeeds(),
            etaSeconds = if (state == DownloadState.Downloading && rate > 0 && hasMetadata()) remaining / rate else null,
            error = error,
        )
    }

    private fun placeholderItem(entry: ActiveEntry) = DownloadItem(
        id = entry.id,
        name = entry.name,
        savePath = entry.savePath,
        state = if (entry.paused) DownloadState.Paused else DownloadState.Starting,
        progress = 0f,
        downloadedBytes = 0L,
        totalBytes = 0L,
        downloadRate = 0,
        uploadRate = 0,
        peers = 0,
        seeds = 0,
        etaSeconds = null,
        error = null,
    )

    /** Shows additions and removals straight away rather than on the next poll. */
    private fun publishPlaceholders() {
        val current = synchronized(this) { entries.values.toList() }
        val known = _downloads.value.associateBy { it.id }
        _downloads.value = current.map { known[it.id] ?: placeholderItem(it) }
    }

    /** Must be called holding the lock. A few hundred bytes, so written inline. */
    private fun persist() {
        val state = TorrentState(active = entries.values.toList(), finished = finishedList)
        runCatching {
            val tmp = File(stateDir, "torrents.json.tmp")
            tmp.writeText(json.encodeToString(TorrentState.serializer(), state))
            if (!tmp.renameTo(stateFile)) {
                stateFile.delete()
                tmp.renameTo(stateFile)
            }
        }
    }
}
