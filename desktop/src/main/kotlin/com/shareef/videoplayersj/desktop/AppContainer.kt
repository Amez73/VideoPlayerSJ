package com.shareef.videoplayersj.desktop

import com.shareef.videoplayersj.desktop.data.LibraryRepository
import com.shareef.videoplayersj.desktop.data.LibraryStore
import com.shareef.videoplayersj.desktop.playback.PlayerController
import com.shareef.videoplayersj.desktop.playback.VlcSetup
import com.shareef.videoplayersj.desktop.thumbnails.ThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing

/** App-wide singletons, created once at startup. */
class AppContainer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

    val libraryRepository = LibraryRepository(LibraryStore(), scope)

    /** Null when libVLC couldn't be found; the UI then explains how to fix it. */
    val playerController: PlayerController? =
        VlcSetup.factory?.let { PlayerController(it, libraryRepository, scope) }

    val thumbnailLoader = ThumbnailLoader(VlcSetup.factory) { videoId, durationMs ->
        libraryRepository.recordDuration(videoId, durationMs)
    }

    suspend fun shutdown() {
        playerController?.release()
        thumbnailLoader.release()
        VlcSetup.factory?.release()
        scope.cancel()
    }
}
