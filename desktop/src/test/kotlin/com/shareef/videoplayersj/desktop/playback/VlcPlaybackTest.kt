package com.shareef.videoplayersj.desktop.playback

import com.shareef.videoplayersj.desktop.thumbnails.FrameGrabber
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the real libVLC pipeline. Runs only where libVLC and a sample video are available —
 * on Windows that's the bundled VLC plus the sample clip Windows ships for its HDR settings page.
 */
class VlcPlaybackTest {

    private val sample = File(
        "C:\\Windows\\SystemResources\\Windows.UI.SettingsAppThreshold\\SystemSettings\\Assets\\SDRSample.mkv",
    )

    private fun available(): Boolean {
        if (!sample.exists()) return false
        assertTrue(VlcSetup.isAvailable, "libVLC not found")
        return true
    }

    @Test
    fun grabsAThumbnailFrame() = runBlocking {
        if (!available()) return@runBlocking
        val grabber = FrameGrabber(VlcSetup.factory!!)
        try {
            val grabbed = assertNotNull(grabber.grab(sample.path))
            assertTrue(grabbed.frame.width > 0 && grabbed.frame.height > 0)
            assertTrue(grabbed.durationMs > 0)
            // Not a blank buffer: some pixel somewhere is non-zero.
            assertTrue(grabbed.frame.pixels.any { it != 0.toByte() })
        } finally {
            grabber.release()
        }
    }

    @Test
    fun deliversFramesWhilePlaying() {
        if (!available()) return
        val frames = CountDownLatch(10)
        val sink = FrameSink { frames.countDown() }
        val player = VlcSetup.factory!!.mediaPlayers().newEmbeddedMediaPlayer()
        try {
            player.videoSurface().set(sink.surface)
            player.media().play(sample.path, ":no-audio")
            assertTrue(frames.await(15, TimeUnit.SECONDS), "VLC delivered fewer than 10 frames")
            assertNotNull(sink.takeLatest())
        } finally {
            player.controls().stop()
            player.release()
        }
    }
}
