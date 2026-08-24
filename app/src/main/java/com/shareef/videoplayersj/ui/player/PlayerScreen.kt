package com.shareef.videoplayersj.ui.player

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.ui.PlayerView
import com.shareef.videoplayersj.MainActivity
import com.shareef.videoplayersj.di.AppContainer
import com.shareef.videoplayersj.ui.player.components.PlayerControls
import com.shareef.videoplayersj.ui.player.components.PlayerTopBar
import com.shareef.videoplayersj.ui.player.components.SeekBar
import com.shareef.videoplayersj.ui.player.components.VolumeControl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun PlayerScreen(
    videoId: Long,
    appContainer: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PlayerViewModel(
                    videoId = videoId,
                    libraryRepository = appContainer.libraryRepository,
                    playbackConnection = appContainer.playbackConnection,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val player by viewModel.player.collectAsState()

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Denial just means no drawer notification — playback and lock-screen controls still work. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // Drive the activity's orientation directly, so the button behaves exactly like physically
    // turning the phone. Sensor landscape (rather than a fixed one) keeps both landscape
    // directions available and ignores the system rotation lock, which is what a fullscreen
    // button is expected to do.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity, isFullscreen) {
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        // Never leave the rest of the app pinned to landscape after leaving the player.
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()

    // --- Picture-in-picture -------------------------------------------------------------
    val pipSupported = remember(context) { isPipSupported(context) }
    val pipModeFlow = remember(activity) {
        (activity as? MainActivity)?.isInPipMode ?: MutableStateFlow(false)
    }
    val isInPipMode by pipModeFlow.collectAsState()

    val enterPip: () -> Unit = {
        if (pipSupported && activity != null) {
            val videoSize = player?.videoSize
            runCatching {
                activity.enterPictureInPictureMode(
                    buildPipParams(
                        context = context,
                        isPlaying = uiState.isPlaying,
                        videoWidth = videoSize?.width ?: 0,
                        videoHeight = videoSize?.height ?: 0,
                        autoEnter = true,
                    ),
                )
            }
        }
    }

    // Keep the PiP window's play/pause button and aspect ratio in step with playback, and (on
    // Android 12+) keep auto-enter armed only while something is actually playing.
    LaunchedEffect(pipSupported, uiState.isPlaying, player) {
        if (!pipSupported || activity == null) return@LaunchedEffect
        val videoSize = player?.videoSize
        runCatching {
            activity.setPictureInPictureParams(
                buildPipParams(
                    context = context,
                    isPlaying = uiState.isPlaying,
                    videoWidth = videoSize?.width ?: 0,
                    videoHeight = videoSize?.height ?: 0,
                    autoEnter = uiState.isPlaying,
                ),
            )
        }
    }

    // Below Android 12 there's no auto-enter, so leaving the app has to trigger PiP by hand.
    DisposableEffect(activity, pipSupported, uiState.isPlaying) {
        val mainActivity = activity as? MainActivity
        if (mainActivity != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            mainActivity.onUserLeaveHintCallback = { if (uiState.isPlaying) enterPip() }
        }
        onDispose { mainActivity?.onUserLeaveHintCallback = null }
    }

    // The PiP window's play/pause button dispatches this broadcast.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == ACTION_PIP_TOGGLE_PLAY) viewModel.togglePlayPause()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_PIP_TOGGLE_PLAY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // Hide the status and navigation bars whenever the HUD is hidden, so tapping the video once
    // leaves nothing but picture — which also matters when screen mirroring to a TV, since
    // Miracast sends the phone's clock, battery and nav buttons along with everything else.
    LaunchedEffect(activity, view, controlsVisible, isInPipMode) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (controlsVisible && !isInPipMode) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Restore the bars once on the way out, rather than on every HUD toggle above.
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Transient "-10s"/"+10s" flash after a double-tap. The tick forces the hide timer to restart
    // even when consecutive taps produce the same label.
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var seekFeedbackOnLeft by remember { mutableStateOf(false) }
    var seekFeedbackTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekFeedbackTick) {
        if (seekFeedback != null) {
            delay(650)
            seekFeedback = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isInPipMode) {
                // In PiP the window is tiny and the system owns the touch behaviour.
                if (isInPipMode) return@pointerInput
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        val onLeft = offset.x < size.width / 2f
                        if (onLeft) viewModel.skipBack() else viewModel.skipForward()
                        seekFeedback = if (onLeft) "− 10s" else "+ 10s"
                        seekFeedbackOnLeft = onLeft
                        seekFeedbackTick++
                    },
                )
            },
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false } },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        seekFeedback?.takeIf { !isInPipMode }?.let { label ->
            Text(
                text = label,
                color = Color.White,
                modifier = Modifier
                    .align(if (seekFeedbackOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 40.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (controlsVisible && !isInPipMode) {
            PlayerTopBar(
                title = uiState.title,
                isCastAvailable = viewModel.isCastAvailable,
                isPipSupported = pipSupported,
                sleepTimerMinutes = sleepTimerMinutes,
                onBack = onBack,
                onEnterPip = enterPip,
                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            VolumeControl(
                onVolumeChange = { viewModel.setVolume(it) },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            )

            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                SeekBar(
                    positionMs = uiState.positionMs,
                    durationMs = uiState.durationMs,
                    onSeek = { viewModel.seekTo(it) },
                )
                PlayerControls(
                    isPlaying = uiState.isPlaying,
                    isFullscreen = isFullscreen,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSkipBack = { viewModel.skipBack() },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                )
            }
        }
    }
}

/** Compose hands out a themed wrapper rather than the Activity itself, so unwrap to reach it. */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
