package com.shareef.videoplayersj.ui.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.ui.PlayerView
import com.shareef.videoplayersj.di.AppContainer
import com.shareef.videoplayersj.ui.player.components.PlayerControls
import com.shareef.videoplayersj.ui.player.components.PlayerTopBar
import com.shareef.videoplayersj.ui.player.components.SeekBar
import com.shareef.videoplayersj.ui.player.components.VolumeControl

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false } },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            PlayerTopBar(
                title = uiState.title,
                onBack = onBack,
                isCastAvailable = viewModel.isCastAvailable,
                modifier = Modifier.align(Alignment.TopCenter),
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
                VolumeControl(onVolumeChange = { viewModel.setVolume(it) })
            }
        }
    }
}

/** Compose hands out a themed wrapper rather than the Activity itself, so unwrap to reach it. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
