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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File

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

    init {
        // Unfinished downloads carry on at launch; otherwise libtorrent isn't loaded until it's needed.
        if (torrentEngine.hasActiveDownloads) scope.launch(Dispatchers.IO) { torrentEngine.start() }
        // A finished download inside a library folder shows up in the library straight away.
        scope.launch {
            torrentEngine.completions.collect { done ->
                val saved = File(done.savePath).absoluteFile.normalize()
                val inLibrary = libraryRepository.folderPaths().any { saved.startsWith(File(it)) }
                if (inLibrary) libraryRepository.rescanAll()
            }
        }
    }

    suspend fun shutdown() {
        withContext(Dispatchers.IO) { torrentEngine.stop() }
        playerController?.release()
        thumbnailLoader.release()
        VlcSetup.factory?.release()
        scope.cancel()
    }
}
