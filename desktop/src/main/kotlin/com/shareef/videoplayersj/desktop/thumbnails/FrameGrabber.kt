package com.shareef.videoplayersj.desktop.thumbnails

import com.shareef.videoplayersj.desktop.playback.FrameSink
import com.shareef.videoplayersj.desktop.playback.VideoFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/** 15% in, to skip black frames, studio logos and fade-ins that open most videos. */
private const val FRAME_POSITION_FRACTION = 0.15
private const val GRAB_TIMEOUT_MS = 12_000L

/** For streams that never report a length: give up on seeking and take a frame this far in. */
private const val FALLBACK_FRAME_COUNT = 48

class GrabbedFrame(val frame: VideoFrame, val durationMs: Long)

/**
 * Pulls a single representative frame out of a video with a silent, invisible VLC player: open
 * it, jump to [FRAME_POSITION_FRACTION] once the length is known, and keep the first frame decoded
 * after the jump. Not thread-safe — callers serialise access.
 */
class FrameGrabber(factory: MediaPlayerFactory) {

    @Volatile private var pending: CompletableDeferred<GrabbedFrame?>? = null
    @Volatile private var targetMs = -1L
    @Volatile private var lengthMs = 0L
    @Volatile private var currentMs = 0L
    @Volatile private var framesSeen = 0
    @Volatile private var framesAtTarget = 0

    private val sink = FrameSink { onFrame() }

    private val player = factory.mediaPlayers().newEmbeddedMediaPlayer().apply {
        videoSurface().set(sink.surface)
        events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (newLength <= 0 || targetMs >= 0) return
                lengthMs = newLength
                val target = (newLength * FRAME_POSITION_FRACTION).toLong()
                targetMs = target
                mediaPlayer.submit { mediaPlayer.controls().setTime(target) }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                currentMs = newTime
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                pending?.complete(null)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                pending?.complete(null)
            }
        })
    }

    private fun onFrame() {
        val deferred = pending ?: return
        framesSeen++
        val target = targetMs
        val ready = when {
            // Two frames past the seek point, so a frame decoded just before the jump isn't used.
            target >= 0 && currentMs >= target - 1_500 -> ++framesAtTarget >= 2
            target < 0 -> framesSeen >= FALLBACK_FRAME_COUNT
            else -> false
        }
        if (!ready) {
            sink.clear()
            return
        }
        val frame = sink.takeLatest() ?: return
        deferred.complete(GrabbedFrame(frame, lengthMs))
    }

    suspend fun grab(path: String): GrabbedFrame? {
        targetMs = -1L
        lengthMs = 0L
        currentMs = 0L
        framesSeen = 0
        framesAtTarget = 0
        val deferred = CompletableDeferred<GrabbedFrame?>()
        pending = deferred
        return try {
            if (!player.media().play(path, ":no-audio", ":no-spu", ":no-sub-autodetect-file")) return null
            withTimeoutOrNull(GRAB_TIMEOUT_MS) { deferred.await() }
        } finally {
            pending = null
            player.controls().stop()
        }
    }

    fun release() {
        player.release()
    }
}
