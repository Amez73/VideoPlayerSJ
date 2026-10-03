package com.shareef.videoplayersj.desktop.playback

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.File

/**
 * Locates libVLC and creates the app-wide [MediaPlayerFactory].
 *
 * The packaged Windows app ships libVLC in its resources folder (see desktop/build.gradle.kts),
 * which is tried first; otherwise vlcj's standard discovery looks for an installed VLC.
 */
object VlcSetup {

    val factory: MediaPlayerFactory? by lazy { createFactory() }

    val isAvailable: Boolean get() = factory != null

    private fun createFactory(): MediaPlayerFactory? {
        val bundled = bundledVlcDir()
        val found = if (bundled != null) {
            // On Windows libVLC finds its plugins relative to its own DLL, so pointing JNA at the
            // folder is all that's needed.
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), bundled.path)
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), bundled.path)
            true
        } else {
            NativeDiscovery().discover()
        }
        if (!found) return null

        return runCatching {
            // A null discovery stops the factory running its own search: it would repeat the work
            // above and, without a hit in the usual places, recursively crawl every folder on PATH.
            MediaPlayerFactory(
                null as NativeDiscovery?,
                listOf("--quiet", "--no-video-title-show", "--no-snapshot-preview", "--no-stats"),
            )
        }.getOrNull()
    }

    private fun bundledVlcDir(): File? {
        val resources = System.getProperty("compose.application.resources.dir") ?: return null
        val dir = File(resources, "vlc")
        return dir.takeIf { File(it, "libvlc.dll").exists() }
    }
}
