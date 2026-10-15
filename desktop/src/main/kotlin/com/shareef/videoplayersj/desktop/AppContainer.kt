package com.shareef.videoplayersj.desktop

import com.shareef.videoplayersj.desktop.data.AppDirs
import com.shareef.videoplayersj.desktop.data.LibraryRepository
import com.shareef.videoplayersj.desktop.data.LibraryStore
import com.shareef.videoplayersj.desktop.data.SettingsStore
import com.shareef.videoplayersj.desktop.input.Gamepad
import com.shareef.videoplayersj.desktop.playback.PlayerController
import com.shareef.videoplayersj.desktop.playback.VlcSetup
import com.shareef.videoplayersj.desktop.thumbnails.ThumbnailLoader
import com.shareef.videoplayersj.desktop.torrent.TorrentNative
import com.shareef.videoplayersj.torrent.TorrentEngine
import com.shareef.videoplayersj.transfer.LocalTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.util.UUID

/** App-wide singletons, created once at startup. */
class AppContainer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

    val libraryRepository = LibraryRepository(LibraryStore(), scope)

    val settingsStore = SettingsStore()

    val gamepad = Gamepad(scope)

    /** Null when libVLC couldn't be found; the UI then explains how to fix it. */
    val playerController: PlayerController? =
        VlcSetup.factory?.let { PlayerController(it, libraryRepository, scope) }

    val thumbnailLoader = ThumbnailLoader(VlcSetup.factory) { videoId, durationMs ->
        libraryRepository.recordDuration(videoId, durationMs)
    }

    val torrentEngine = TorrentEngine(File(AppDirs.dataDir, "torrents"), scope, beforeStart = TorrentNative::prepare)

    /** How this computer appears to the phone when sending files. */
    val deviceName: String = System.getenv("COMPUTERNAME")
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: "Computer"

    /** Sends files to and from the Android app on the same Wi-Fi. */
    val localTransfer = LocalTransfer(
        deviceId = settingsStore.settings.deviceId ?: UUID.randomUUID().toString().also { id ->
            settingsStore.update { it.copy(deviceId = id) }
        },
        deviceName = deviceName,
        platform = System.getProperty("os.name").lowercase().let {
            when {
                it.startsWith("windows") -> "windows"
                it.contains("mac") -> "mac"
                else -> "linux"
            }
        },
        scope = scope,
    )

    init {
        localTransfer.start()
        scope.launch {
            localTransfer.received.collect { received -> rescanIfInLibrary(received.dir) }
        }
        // Unfinished downloads carry on at launch; otherwise libtorrent isn't loaded until it's needed.
        if (torrentEngine.hasActiveDownloads) scope.launch(Dispatchers.IO) { torrentEngine.start() }
        scope.launch {
            torrentEngine.completions.collect { done -> rescanIfInLibrary(done.savePath) }
        }
    }

    /** Finished downloads and received files inside a library folder show up in the library straight away. */
    private suspend fun rescanIfInLibrary(path: String) {
        val saved = File(path).absoluteFile.normalize()
        if (libraryRepository.folderPaths().any { saved.startsWith(File(it)) }) libraryRepository.rescanAll()
    }

    suspend fun shutdown() {
        localTransfer.stop()
        withContext(Dispatchers.IO) { torrentEngine.stop() }
        playerController?.release()
        thumbnailLoader.release()
        VlcSetup.factory?.release()
        scope.cancel()
    }
}
