package com.shareef.videoplayersj.desktop.playback

import com.shareef.videoplayersj.desktop.data.LibraryRepository
import com.shareef.videoplayersj.desktop.model.LibraryVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
const val SKIP_MS = 10_000L
const val MAX_VOLUME = 150

data class Track(val id: Int, val name: String)

data class PlaybackState(
    val video: LibraryVideo? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 100,
    val isMuted: Boolean = false,
    val hasEnded: Boolean = false,
    val error: String? = null,
    val audioTracks: List<Track> = emptyList(),
    val selectedAudioTrack: Int = -1,
    val subtitleTracks: List<Track> = emptyList(),
    val selectedSubtitleTrack: Int = -1,
)

/**
 * The app's single playback session, wrapping one libVLC media player.
 *
 * VLC raises events on its own threads and forbids calling back into the player from them, so
 * any follow-up call made from an event goes through [MediaPlayer.submit].
 */
class PlayerController(
    factory: MediaPlayerFactory,
    private val repository: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val frameSignals = Channel<Unit>(Channel.CONFLATED)
    val frameSink = FrameSink { frameSignals.trySend(Unit) }

    /** Emits whenever a new frame is waiting in [frameSink]; bursts are merged into one. */
    val frames: Flow<Unit> = frameSignals.receiveAsFlow()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var progressJob: Job? = null

    private val mediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer().apply {
        videoSurface().set(frameSink.surface)
        events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(isPlaying = true, hasEnded = false, error = null) }
                // The audio output only exists once playback starts, so volume set earlier is lost.
                mediaPlayer.submit {
                    mediaPlayer.audio().setVolume(_state.value.volume)
                    mediaPlayer.audio().setMute(_state.value.isMuted)
                    refreshTracks(mediaPlayer)
                }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(isPlaying = false) }
                saveProgress(isFinished = false)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(isPlaying = false) }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(isPlaying = false, hasEnded = true, positionMs = it.durationMs) }
                saveProgress(isFinished = true)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(isPlaying = false, error = "This file couldn't be played.") }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _state.update { it.copy(positionMs = newTime) }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (newLength > 0) _state.update { it.copy(durationMs = newLength) }
            }

            override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: TrackType, id: Int) {
                mediaPlayer.submit { refreshTracks(mediaPlayer) }
            }

            override fun elementaryStreamDeleted(mediaPlayer: MediaPlayer, type: TrackType, id: Int) {
                mediaPlayer.submit { refreshTracks(mediaPlayer) }
            }

            override fun elementaryStreamSelected(mediaPlayer: MediaPlayer, type: TrackType, id: Int) {
                mediaPlayer.submit { refreshTracks(mediaPlayer) }
            }
        })
    }

    fun play(video: LibraryVideo) {
        if (_state.value.video != null) saveProgress(isFinished = _state.value.hasEnded)
        val startMs = repository.resumePositionFor(video.id)
        _state.update {
            PlaybackState(
                video = video,
                positionMs = startMs,
                durationMs = video.durationMs ?: 0L,
                volume = it.volume,
                isMuted = it.isMuted,
            )
        }
        frameSink.clear()
        val options = if (startMs > 0) arrayOf(":start-time=${startMs / 1000.0}") else emptyArray()
        if (!mediaPlayer.media().play(video.path, *options)) {
            _state.update { it.copy(error = "This file couldn't be opened.") }
        }
        startProgressSaving()
    }

    fun togglePlayPause() {
        val current = _state.value
        when {
            current.video == null -> return
            // Once a file has ended VLC needs it restarting rather than un-pausing.
            current.hasEnded -> current.video.let { video ->
                _state.update { it.copy(hasEnded = false, positionMs = 0L) }
                mediaPlayer.media().play(video.path)
            }
            current.isPlaying -> mediaPlayer.controls().setPause(true)
            else -> mediaPlayer.controls().play()
        }
    }

    fun seekTo(positionMs: Long) {
        val duration = _state.value.durationMs
        val target = if (duration > 0) positionMs.coerceIn(0L, duration - 500) else positionMs.coerceAtLeast(0L)
        mediaPlayer.controls().setTime(target)
        // Reflect the new position immediately rather than waiting for VLC's next time event.
        _state.update { it.copy(positionMs = target, hasEnded = false) }
    }

    fun skip(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    fun setVolume(volume: Int) {
        val clamped = volume.coerceIn(0, MAX_VOLUME)
        _state.update { it.copy(volume = clamped, isMuted = false) }
        mediaPlayer.audio().setVolume(clamped)
        mediaPlayer.audio().setMute(false)
    }

    fun toggleMute() {
        val muted = !_state.value.isMuted
        _state.update { it.copy(isMuted = muted) }
        mediaPlayer.audio().setMute(muted)
    }

    fun selectAudioTrack(id: Int) {
        mediaPlayer.audio().setTrack(id)
        _state.update { it.copy(selectedAudioTrack = id) }
    }

    fun selectSubtitleTrack(id: Int) {
        mediaPlayer.subpictures().setTrack(id)
        _state.update { it.copy(selectedSubtitleTrack = id) }
    }

    /** Stops playback and records where it got to. */
    fun stop() {
        if (_state.value.video == null) return
        saveProgress(isFinished = _state.value.hasEnded)
        progressJob?.cancel()
        mediaPlayer.controls().stop()
        frameSink.clear()
        _state.update { PlaybackState(volume = it.volume, isMuted = it.isMuted) }
    }

    /** For app exit: unlike [stop], waits for the final position to be written to disk. */
    suspend fun release() {
        progressJob?.cancel()
        val snapshot = _state.value
        val video = snapshot.video
        if (video != null && snapshot.positionMs > 0L) {
            repository.saveProgress(video.id, snapshot.positionMs, snapshot.durationMs, snapshot.hasEnded)
        }
        mediaPlayer.controls().stop()
        mediaPlayer.release()
    }

    private fun refreshTracks(mediaPlayer: MediaPlayer) {
        val audio = mediaPlayer.audio().trackDescriptions().map { Track(it.id(), it.description()) }
        val subtitles = mediaPlayer.subpictures().trackDescriptions().map { Track(it.id(), it.description()) }
        _state.update {
            it.copy(
                audioTracks = audio,
                selectedAudioTrack = mediaPlayer.audio().track(),
                subtitleTracks = subtitles,
                selectedSubtitleTrack = mediaPlayer.subpictures().track(),
            )
        }
    }

    private fun startProgressSaving() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                if (_state.value.isPlaying) saveProgress(isFinished = false)
            }
        }
    }

    private fun saveProgress(isFinished: Boolean) {
        val snapshot = _state.value
        val video = snapshot.video ?: return
        val position = if (isFinished) snapshot.durationMs else snapshot.positionMs
        if (position <= 0L && !isFinished) return
        scope.launch {
            repository.saveProgress(video.id, position, snapshot.durationMs, isFinished)
        }
    }
}
