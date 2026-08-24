@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.shareef.videoplayersj.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.cast.framework.CastContext
import com.google.common.util.concurrent.ListenableFuture
import com.shareef.videoplayersj.data.repository.WatchProgressRepository
import com.shareef.videoplayersj.model.LibraryVideo
import com.shareef.videoplayersj.playback.cast.CastMediaServer
import com.shareef.videoplayersj.playback.cast.LanAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NowPlaying(
    val videoId: Long,
    val title: String,
    val subtitle: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

private const val RESUME_MIN_MS = 5_000L
private const val RESUME_MAX_FRACTION = 0.95
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
private const val SKIP_BACK_MS = 10_000L

/**
 * The single, app-scoped connection to [PlaybackService]'s [MediaController] — owns the one
 * playback session for the whole app so the mini-player, the full player screen, the system
 * notification, and the lock screen are all reading/driving the same state and can never disagree.
 * Connects lazily on first [playVideo] so browsing the library never spins up the service.
 *
 * Also owns the (optional) Cast session: a [CastPlayer] is built alongside the local
 * [MediaController], and [player] transparently points at whichever of the two is currently
 * active. The local [MediaController] is paused (not torn down) while casting, so switching back
 * is a plain seek+resume rather than a full reconnect.
 */
class PlaybackConnection(
    context: Context,
    private val watchProgressRepository: WatchProgressRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingConnect: CompletableDeferred<MediaController>? = null
    private var tickerJob: Job? = null
    private var hasPrepared = false
    private var lastSavedAt = 0L

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null
    private val castMediaServer = CastMediaServer(appContext)

    /** The video the user asked to play/cast most recently — needed because a [MediaItem] read
     * back off a [MediaController] has its source [Uri] stripped, so it can't be recovered from
     * `controller.currentMediaItem` when a cast session starts. */
    private var currentVideo: LibraryVideo? = null

    /** False on devices without (working) Google Play Services — the Cast button should hide. */
    val isCastAvailable: Boolean get() = castPlayer != null

    /** A listener that only acts when [playerRef] is the currently active player, so events from
     * a paused/backgrounded player (e.g. the local controller while casting) don't leak through. */
    private fun makeListener(playerRef: () -> Player?): Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (playerRef() !== _player.value) {
                // e.g. a stray Play tap on the paused local notification while casting — undo it.
                if (isPlaying) playerRef()?.pause()
                return
            }
            refreshNowPlaying()
            if (!isPlaying) saveProgress(isFinished = false)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playerRef() !== _player.value) return
            when (playbackState) {
                Player.STATE_READY, Player.STATE_BUFFERING -> {
                    hasPrepared = true
                    refreshNowPlaying()
                }
                Player.STATE_ENDED -> {
                    saveProgress(isFinished = true)
                    teardown()
                }
                Player.STATE_IDLE -> {
                    // Only treat this as "stopped externally" (e.g. notification swipe-away sends
                    // COMMAND_STOP) once we know playback had actually started — STATE_IDLE is also
                    // the state a MediaItem sits in before prepare() is called.
                    if (hasPrepared) {
                        saveProgress(isFinished = false)
                        teardown()
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (playerRef() !== _player.value) return
            refreshNowPlaying()
        }
    }

    private val localPlayerListener = makeListener { controller }
    private val castPlayerListener = makeListener { castPlayer }

    private val castSessionListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            val video = currentVideo ?: return
            val cp = castPlayer ?: return
            val startPositionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            if (!loadIntoCastPlayer(video, cp, startPositionMs)) return
            controller?.pause()
            _player.value = cp
            refreshNowPlaying()
        }

        override fun onCastSessionUnavailable() {
            val cp = castPlayer ?: return
            val finalPositionMs = cp.currentPosition.coerceAtLeast(0L)
            val wasPlaying = cp.isPlaying
            castMediaServer.stopServing()
            cp.clearMediaItems()
            _player.value = controller
            controller?.let {
                it.seekTo(finalPositionMs.coerceAtMost(it.duration.coerceAtLeast(0L)))
                if (wasPlaying) it.play()
            }
            refreshNowPlaying()
        }
    }

    init {
        castPlayer = buildCastPlayer()
    }

    @Suppress("DEPRECATION")
    private fun buildCastPlayer(): CastPlayer? = try {
        val ctx = CastContext.getSharedInstance(appContext)
        castContext = ctx
        CastPlayer(ctx).apply {
            setSessionAvailabilityListener(castSessionListener)
            addListener(castPlayerListener)
        }
    } catch (e: Exception) {
        null
    }

    suspend fun playVideo(video: LibraryVideo) {
        val cp = castPlayer
        if (cp != null && cp.isCastSessionAvailable) {
            castVideo(video, cp)
            return
        }

        val c = awaitController()
        currentVideo = video

        if (c.currentMediaItem?.mediaId == video.id.toString()) {
            // Already the loaded/playing item — e.g. reopening the player screen, or tapping the
            // mini-player. Don't restart it.
            return
        }

        val outgoingVideoId = c.currentMediaItem?.mediaId?.toLongOrNull()
        if (outgoingVideoId != null) {
            val position = c.currentPosition.coerceAtLeast(0L)
            val duration = c.duration.coerceAtLeast(0L)
            if (duration > 0L) {
                scope.launch { watchProgressRepository.saveProgress(outgoingVideoId, position, duration, false) }
            }
        }

        val startPositionMs = resumePositionFor(video.id)
        val mediaItem = MediaItem.Builder()
            .setMediaId(video.id.toString())
            .setUri(Uri.parse(video.documentUriString))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.displayTitle)
                    .setArtist(buildSubtitle(video))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

        hasPrepared = false
        c.setMediaItem(mediaItem, startPositionMs)
        c.prepare()
        c.play()
        refreshNowPlaying()
    }

    private suspend fun castVideo(video: LibraryVideo, cp: CastPlayer) {
        if (cp.currentMediaItem?.mediaId == video.id.toString()) {
            currentVideo = video
            return
        }

        val outgoingVideoId = cp.currentMediaItem?.mediaId?.toLongOrNull()
        if (outgoingVideoId != null) {
            val position = cp.currentPosition.coerceAtLeast(0L)
            val duration = cp.duration.coerceAtLeast(0L)
            if (duration > 0L) {
                scope.launch { watchProgressRepository.saveProgress(outgoingVideoId, position, duration, false) }
            }
        }

        currentVideo = video
        loadIntoCastPlayer(video, cp, resumePositionFor(video.id))
    }

    /** Starts serving [video] over the local HTTP server and loads it into [cp]. Returns false
     * (leaving [cp] untouched) if there's no LAN address to serve from, e.g. WiFi is off. */
    private fun loadIntoCastPlayer(video: LibraryVideo, cp: CastPlayer, startPositionMs: Long): Boolean {
        val host = LanAddress.currentIPv4(appContext) ?: return false
        val sourceUri = Uri.parse(video.documentUriString)
        val url = castMediaServer.serve(video.id, sourceUri, host)
        val mimeType = castMediaServer.mimeTypeForUri(sourceUri)

        val mediaItem = MediaItem.Builder()
            .setMediaId(video.id.toString())
            .setUri(Uri.parse(url))
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.displayTitle)
                    .setArtist(buildSubtitle(video))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

        hasPrepared = false
        cp.setMediaItem(mediaItem, startPositionMs)
        cp.prepare()
        cp.play()
        refreshNowPlaying()
        return true
    }

    fun togglePlayPause() {
        _player.value?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipBack() {
        _player.value?.let { it.seekTo((it.currentPosition - SKIP_BACK_MS).coerceAtLeast(0L)) }
        // Reflect the new position immediately rather than waiting for the next 500ms tick.
        refreshNowPlaying()
    }

    fun seekTo(positionMs: Long) {
        _player.value?.let { it.seekTo(positionMs.coerceIn(0L, it.duration.coerceAtLeast(0L))) }
        refreshNowPlaying()
    }

    fun setVolume(volume: Float) {
        _player.value?.volume = volume.coerceIn(0f, 1f)
    }

    fun stopAndDismiss() {
        saveProgress(isFinished = false)
        if (castPlayer?.isCastSessionAvailable == true) {
            castContext?.sessionManager?.endCurrentSession(true)
        }
        teardown()
    }

    private suspend fun awaitController(): MediaController {
        controller?.let { return it }
        pendingConnect?.let { return it.await() }

        val deferred = CompletableDeferred<MediaController>()
        pendingConnect = deferred

        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val connected = future.get()
                    onConnected(connected)
                    deferred.complete(connected)
                } catch (error: Exception) {
                    pendingConnect = null
                    deferred.completeExceptionally(error)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
        return deferred.await()
    }

    private fun onConnected(mediaController: MediaController) {
        controller = mediaController
        pendingConnect = null
        mediaController.addListener(localPlayerListener)
        _player.value = mediaController
        refreshNowPlaying()
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val c = _player.value
                if (c != null && c.isPlaying) {
                    refreshNowPlaying()
                    maybeSaveProgress()
                }
                delay(500)
            }
        }
    }

    private fun refreshNowPlaying() {
        val c = _player.value
        val item = c?.currentMediaItem
        val videoId = item?.mediaId?.toLongOrNull()
        if (c == null || item == null || videoId == null) {
            _nowPlaying.value = null
            return
        }
        _nowPlaying.value = NowPlaying(
            videoId = videoId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            subtitle = item.mediaMetadata.artist?.toString(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
        )
    }

    private fun maybeSaveProgress() {
        val now = System.currentTimeMillis()
        if (now - lastSavedAt >= PROGRESS_SAVE_INTERVAL_MS) {
            lastSavedAt = now
            saveProgress(isFinished = false)
        }
    }

    private fun saveProgress(isFinished: Boolean) {
        val c = _player.value ?: return
        val videoId = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val position = c.currentPosition.coerceAtLeast(0L)
        val duration = c.duration.coerceAtLeast(0L)
        if (duration <= 0L) return
        scope.launch { watchProgressRepository.saveProgress(videoId, position, duration, isFinished) }
    }

    private suspend fun resumePositionFor(videoId: Long): Long {
        val progress = watchProgressRepository.getProgress(videoId) ?: return 0L
        if (progress.isFinished || progress.durationMs <= 0L) return 0L
        val resumeCeiling = (progress.durationMs * RESUME_MAX_FRACTION).toLong()
        return if (progress.positionMs in RESUME_MIN_MS until resumeCeiling) progress.positionMs else 0L
    }

    private fun buildSubtitle(video: LibraryVideo): String? {
        val seasonEpisode = if (video.season != null && video.episode != null) {
            "S%02dE%02d".format(video.season, video.episode)
        } else {
            video.episode?.let { "Episode $it" }
        }
        return when {
            seasonEpisode != null && !video.episodeTitle.isNullOrBlank() -> "$seasonEpisode · ${video.episodeTitle}"
            seasonEpisode != null -> seasonEpisode
            !video.episodeTitle.isNullOrBlank() -> video.episodeTitle
            else -> null
        }
    }

    private fun teardown() {
        controller?.pause()
        controller?.clearMediaItems()
        controller?.removeListener(localPlayerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        tickerJob?.cancel()
        tickerJob = null
        controller = null
        controllerFuture = null
        pendingConnect = null
        hasPrepared = false
        currentVideo = null
        if (castPlayer?.isCastSessionAvailable == true) {
            castMediaServer.stopServing()
            castPlayer?.clearMediaItems()
        }
        _player.value = null
        _nowPlaying.value = null
    }
}
