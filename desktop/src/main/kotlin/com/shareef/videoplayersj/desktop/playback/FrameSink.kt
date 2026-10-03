package com.shareef.videoplayersj.desktop.playback

import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

private const val DEFAULT_ASPECT = 16f / 9f

/** One decoded frame in RV32 layout: 4 bytes per pixel, B-G-R-X in memory. */
class VideoFrame(
    val width: Int,
    val height: Int,
    /** Display aspect ratio, which differs from width/height for anamorphic video. */
    val aspectRatio: Float,
    val pixels: ByteArray,
)

/**
 * Asks libVLC to decode into memory instead of a native window, so the picture can be drawn by
 * Compose like any other content — which is what lets the player controls float over the video.
 *
 * Only the newest frame is kept: if the UI falls behind, older frames are dropped rather than
 * queued. Pixel arrays are recycled, since a 1080p frame is 8 MB and arrives 24–60 times a second.
 */
class FrameSink(private val onFrameAvailable: () -> Unit = {}) {

    @Volatile private var aspectRatio = DEFAULT_ASPECT

    private val latest = AtomicReference<VideoFrame?>(null)
    private val pool = ArrayDeque<ByteArray>()

    /** Takes the newest frame, if one arrived since the last call. Hand it back via [recycle]. */
    fun takeLatest(): VideoFrame? = latest.getAndSet(null)

    fun recycle(frame: VideoFrame) {
        synchronized(pool) {
            if (pool.size < 3) pool.addLast(frame.pixels)
        }
    }

    fun clear() {
        latest.getAndSet(null)?.let(::recycle)
    }

    private fun obtainArray(size: Int): ByteArray = synchronized(pool) {
        while (pool.isNotEmpty()) {
            val candidate = pool.removeFirst()
            if (candidate.size == size) return candidate
        }
        ByteArray(size)
    }

    private val formatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            aspectRatio = if (sourceHeight > 0) sourceWidth.toFloat() / sourceHeight else DEFAULT_ASPECT
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {
            if (displayWidth > 0 && displayHeight > 0) {
                aspectRatio = displayWidth.toFloat() / displayHeight
            }
        }

        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
    }

    private val renderCallback = object : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) = Unit

        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<out ByteBuffer>,
            bufferFormat: BufferFormat,
            displayWidth: Int,
            displayHeight: Int,
        ) {
            // The native buffer is only valid during this call, so copy it out.
            val buffer = nativeBuffers[0]
            buffer.rewind()
            val pixels = obtainArray(buffer.remaining())
            buffer.get(pixels)
            val aspect = if (displayWidth > 0 && displayHeight > 0) {
                displayWidth.toFloat() / displayHeight
            } else {
                aspectRatio
            }
            val frame = VideoFrame(bufferFormat.width, bufferFormat.height, aspect, pixels)
            latest.getAndSet(frame)?.let(::recycle)
            onFrameAvailable()
        }

        override fun unlock(mediaPlayer: MediaPlayer) = Unit
    }

    val surface = CallbackVideoSurface(
        formatCallback,
        renderCallback,
        true,
        VideoSurfaceAdapters.getVideoSurfaceAdapter(),
    )
}
