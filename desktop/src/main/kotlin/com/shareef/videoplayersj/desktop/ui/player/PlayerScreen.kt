package com.shareef.videoplayersj.desktop.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shareef.videoplayersj.desktop.data.LibraryRepository
import com.shareef.videoplayersj.desktop.model.LibraryVideo
import com.shareef.videoplayersj.desktop.playback.MAX_VOLUME
import com.shareef.videoplayersj.desktop.playback.PlayerController
import com.shareef.videoplayersj.desktop.playback.SKIP_MS
import com.shareef.videoplayersj.desktop.playback.Track
import com.shareef.videoplayersj.util.formatDuration
import kotlinx.coroutines.delay
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

private const val CONTROLS_HIDE_DELAY_MS = 2_500L
private const val UP_NEXT_COUNTDOWN_SECONDS = 10
private const val VOLUME_STEP = 5

private val BlankPointer = PointerIcon(
    Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank"),
)

/** Lets the window route key presses to whichever screen currently wants them. */
class KeyHandlerHost {
    var handler: ((KeyEvent) -> Boolean)? = null
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    initialVideo: LibraryVideo,
    controller: PlayerController,
    repository: LibraryRepository,
    keyHandlerHost: KeyHandlerHost,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
) {
    var video by remember { mutableStateOf(initialVideo) }
    val state by controller.state.collectAsState()
    val view by repository.view.collectAsState()
    val showTitle = video.showId?.let { id -> view.shows.firstOrNull { it.id == id }?.canonicalTitle }
    val nextEpisode = remember(video.id, view) { repository.nextEpisodeAfter(video.id) }

    LaunchedEffect(video.id) { controller.play(video) }
    DisposableEffect(Unit) { onDispose { controller.stop() } }

    // --- HUD visibility: shown on mouse movement, hidden after a pause while playing -----------
    var lastActivity by remember { mutableLongStateOf(System.nanoTime()) }
    var pointerOverControls by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(lastActivity, state.isPlaying, pointerOverControls, menuOpen) {
        controlsVisible = true
        if (state.isPlaying && !pointerOverControls && !menuOpen) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }
    val poke = { lastActivity = System.nanoTime() }

    // --- Transient feedback ("+10s", "Volume 80%") for keyboard actions ----------------------
    var flash by remember { mutableStateOf<String?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashTick) {
        if (flash != null) {
            delay(700)
            flash = null
        }
    }
    fun showFlash(text: String) {
        flash = text
        flashTick++
    }

    val playNext: () -> Unit = { nextEpisode?.let { video = it } }

    DisposableEffect(keyHandlerHost, nextEpisode, isFullscreen) {
        keyHandlerHost.handler = handler@{ event ->
            if (event.type != KeyEventType.KeyDown) return@handler false
            val current = controller.state.value
            when (event.key) {
                Key.Spacebar, Key.K -> controller.togglePlayPause()
                Key.DirectionLeft, Key.J -> {
                    controller.skip(-SKIP_MS)
                    showFlash("− 10s")
                }
                Key.DirectionRight, Key.L -> {
                    controller.skip(SKIP_MS)
                    showFlash("+ 10s")
                }
                Key.DirectionUp -> {
                    controller.setVolume(current.volume + VOLUME_STEP)
                    showFlash("Volume ${controller.state.value.volume}%")
                }
                Key.DirectionDown -> {
                    controller.setVolume(current.volume - VOLUME_STEP)
                    showFlash("Volume ${controller.state.value.volume}%")
                }
                Key.M -> {
                    controller.toggleMute()
                    showFlash(if (controller.state.value.isMuted) "Muted" else "Volume ${current.volume}%")
                }
                Key.F, Key.Enter -> onToggleFullscreen()
                Key.N -> playNext()
                Key.Escape -> if (isFullscreen) onToggleFullscreen() else onBack()
                Key.Backspace -> onBack()
                else -> return@handler false
            }
            poke()
            true
        }
        onDispose { keyHandlerHost.handler = null }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerHoverIcon(if (controlsVisible) PointerIcon.Default else BlankPointer)
            .onPointerEvent(PointerEventType.Move) { poke() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controller.togglePlayPause()
                        poke()
                    },
                    onDoubleTap = { onToggleFullscreen() },
                )
            },
    ) {
        VideoSurface(controller, Modifier.fillMaxSize())

        flash?.let { label ->
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }

        // Paused: a big play button in the middle, as every desktop player does.
        if (!state.isPlaying && !state.hasEnded && state.error == null && flash == null && state.positionMs > 0) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(84.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
        }

        state.error?.let { message ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    video.path,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                Button(onClick = onBack) { Text("Back to library") }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TopBar(
                    title = showTitle ?: video.displayTitle,
                    subtitle = if (showTitle != null) video.episodeLabel else null,
                    onBack = onBack,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onPointerEvent(PointerEventType.Enter) { pointerOverControls = true }
                        .onPointerEvent(PointerEventType.Exit) { pointerOverControls = false },
                )
                BottomControls(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    hasEnded = state.hasEnded,
                    volume = state.volume,
                    isMuted = state.isMuted,
                    hasNext = nextEpisode != null,
                    isFullscreen = isFullscreen,
                    audioTracks = state.audioTracks,
                    selectedAudioTrack = state.selectedAudioTrack,
                    subtitleTracks = state.subtitleTracks,
                    selectedSubtitleTrack = state.selectedSubtitleTrack,
                    onSeek = controller::seekTo,
                    onPlayPause = controller::togglePlayPause,
                    onSkip = controller::skip,
                    onNext = playNext,
                    onVolumeChange = controller::setVolume,
                    onToggleMute = controller::toggleMute,
                    onToggleFullscreen = onToggleFullscreen,
                    onSelectAudio = controller::selectAudioTrack,
                    onSelectSubtitle = controller::selectSubtitleTrack,
                    onMenuOpenChange = { menuOpen = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onPointerEvent(PointerEventType.Enter) { pointerOverControls = true }
                        .onPointerEvent(PointerEventType.Exit) { pointerOverControls = false },
                )
            }
        }

        if (state.hasEnded) {
            EndOfVideoCard(
                next = nextEpisode,
                onPlayNext = playNext,
                onReplay = controller::togglePlayPause,
                onBack = onBack,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 140.dp),
            )
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            .padding(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 36.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BottomControls(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasEnded: Boolean,
    volume: Int,
    isMuted: Boolean,
    hasNext: Boolean,
    isFullscreen: Boolean,
    audioTracks: List<Track>,
    selectedAudioTrack: Int,
    subtitleTracks: List<Track>,
    selectedSubtitleTrack: Int,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onNext: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = if (isDragging) dragValue else (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownPosition = if (isDragging) (dragValue * duration).toLong() else positionMs

    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 12.dp),
    ) {
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek((dragValue * duration).toLong())
                isDragging = false
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudButton(
                icon = when {
                    hasEnded -> Icons.Default.Replay
                    isPlaying -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                description = if (isPlaying) "Pause (Space)" else "Play (Space)",
                onClick = onPlayPause,
                large = true,
            )
            HudButton(Icons.Default.Replay10, "Back 10 seconds (←)", onClick = { onSkip(-SKIP_MS) })
            HudButton(Icons.Default.Forward10, "Forward 10 seconds (→)", onClick = { onSkip(SKIP_MS) })
            if (hasNext) HudButton(Icons.Default.SkipNext, "Next episode (N)", onClick = onNext)

            HudButton(
                icon = when {
                    isMuted || volume == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                    volume < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                description = if (isMuted) "Unmute (M)" else "Mute (M)",
                onClick = onToggleMute,
            )
            Slider(
                value = if (isMuted) 0f else volume.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = 0f..MAX_VOLUME.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
                modifier = Modifier.width(120.dp),
            )

            Text(
                "${formatDuration(shownPosition)} / ${formatDuration(durationMs)}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp),
            )

            Spacer(Modifier.weight(1f))

            if (audioTracks.size > 2) {
                TrackMenuButton(Icons.Default.Audiotrack, "Audio track", audioTracks, selectedAudioTrack, onSelectAudio, onMenuOpenChange)
            }
            if (subtitleTracks.isNotEmpty()) {
                TrackMenuButton(Icons.Default.Subtitles, "Subtitles", subtitleTracks, selectedSubtitleTrack, onSelectSubtitle, onMenuOpenChange)
            }
            HudButton(
                icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                description = if (isFullscreen) "Exit fullscreen (F)" else "Fullscreen (F)",
                onClick = onToggleFullscreen,
            )
        }
    }
}

@Composable
private fun HudButton(icon: ImageVector, description: String, onClick: () -> Unit, large: Boolean = false) {
    IconButton(onClick = onClick, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(if (large) 34.dp else 26.dp))
    }
}

@Composable
private fun TrackMenuButton(
    icon: ImageVector,
    description: String,
    tracks: List<Track>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    fun setOpen(value: Boolean) {
        open = value
        onMenuOpenChange(value)
    }
    Box {
        HudButton(icon, description, onClick = { setOpen(true) })
        DropdownMenu(expanded = open, onDismissRequest = { setOpen(false) }) {
            Text(
                description,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.name) },
                    trailingIcon = if (track.id == selected) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(track.id)
                        setOpen(false)
                    },
                )
            }
        }
    }
}

/** Shown once a video finishes: counts down into the next episode, or offers a replay. */
@Composable
private fun EndOfVideoCard(
    next: LibraryVideo?,
    onPlayNext: () -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var secondsLeft by remember(next?.id) { mutableIntStateOf(UP_NEXT_COUNTDOWN_SECONDS) }
    var cancelled by remember(next?.id) { mutableStateOf(false) }
    LaunchedEffect(next?.id, cancelled) {
        if (next == null || cancelled) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        onPlayNext()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        modifier = modifier.width(360.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (next != null) {
                Text(
                    if (cancelled) "Up next" else "Up next in $secondsLeft",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    next.episodeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                )
                Row {
                    Button(onClick = onPlayNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Play now", modifier = Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    if (!cancelled) {
                        OutlinedButton(onClick = { cancelled = true }) { Text("Cancel") }
                    } else {
                        OutlinedButton(onClick = onBack) { Text("Back") }
                    }
                }
            } else {
                Text("Finished", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 14.dp))
                Row {
                    Button(onClick = onReplay) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Replay", modifier = Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onBack) { Text("Back to library") }
                }
            }
        }
    }
}
