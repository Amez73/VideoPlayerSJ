package com.shareef.videoplayersj.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.shareef.videoplayersj.data.repository.LibraryRepository
import com.shareef.videoplayersj.playback.PlaybackConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Thin adapter over the app-wide [PlaybackConnection] for a specific video: this screen owns no
 * playback state of its own, since playback must survive navigating away from it (the mini-player
 * keeps driving the same shared session). All this ViewModel does is (a) tell the connection to
 * load/adopt this video, and (b) project the connection's shared state down into this screen's UI
 * state, ignoring updates that belong to some other video the connection might switch to next.
 */
class PlayerViewModel(
    private val videoId: Long,
    libraryRepository: LibraryRepository,
    private val playbackConnection: PlaybackConnection,
) : ViewModel() {

    val player: StateFlow<Player?> = playbackConnection.player

    val isCastAvailable: Boolean = playbackConnection.isCastAvailable

    val uiState: StateFlow<PlayerUiState> = combine(
        libraryRepository.observeVideo(videoId),
        playbackConnection.nowPlaying,
    ) { video, nowPlaying ->
        val mine = nowPlaying?.takeIf { it.videoId == videoId }
        PlayerUiState(
            title = video?.displayTitle.orEmpty(),
            isPlaying = mine?.isPlaying ?: false,
            positionMs = mine?.positionMs ?: 0L,
            durationMs = mine?.durationMs ?: 0L,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    init {
        viewModelScope.launch {
            libraryRepository.observeVideo(videoId).first()?.let { video ->
                playbackConnection.playVideo(video)
            }
        }
    }

    fun togglePlayPause() = playbackConnection.togglePlayPause()

    fun skipBack() = playbackConnection.skipBack()

    fun seekTo(positionMs: Long) = playbackConnection.seekTo(positionMs)

    fun setVolume(volume: Float) = playbackConnection.setVolume(volume)
}
