package com.shareef.videoplayersj.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.shareef.videoplayersj.desktop.model.LibraryVideo
import com.shareef.videoplayersj.desktop.ui.common.LocalThumbnailLoader
import com.shareef.videoplayersj.desktop.ui.library.LibraryScreen
import com.shareef.videoplayersj.desktop.ui.player.KeyHandlerHost
import com.shareef.videoplayersj.desktop.ui.player.PlayerScreen
import com.shareef.videoplayersj.desktop.ui.showdetail.ShowDetailScreen
import com.shareef.videoplayersj.desktop.ui.theme.VideoPlayerSJTheme
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import javax.swing.UIManager

private sealed interface Screen {
    data object Library : Screen
    data class ShowDetail(val showId: Long) : Screen
    data class Player(val video: LibraryVideo) : Screen
}

fun main() {
    // Gives the folder chooser the native Explorer/Finder look rather than Swing's default.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val container = AppContainer()

    application {
        val windowState = rememberWindowState(
            width = 1360.dp,
            height = 860.dp,
            position = WindowPosition(Alignment.Center),
        )
        val keyHandlerHost = remember { KeyHandlerHost() }
        var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Library)) }
        // Fullscreen is remembered as the placement to return to, since the window may have been maximized.
        var placementBeforeFullscreen by remember { mutableStateOf(WindowPlacement.Floating) }

        val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
        fun setFullscreen(fullscreen: Boolean) {
            if (fullscreen == isFullscreen) return
            if (fullscreen) {
                placementBeforeFullscreen = windowState.placement
                windowState.placement = WindowPlacement.Fullscreen
            } else {
                windowState.placement = placementBeforeFullscreen
            }
        }

        fun navigate(screen: Screen) {
            backStack = backStack + screen
        }

        fun goBack() {
            if (backStack.last() is Screen.Player) setFullscreen(false)
            if (backStack.size > 1) backStack = backStack.dropLast(1)
        }

        val playVideo: (LibraryVideo) -> Unit = { video ->
            if (container.playerController != null) navigate(Screen.Player(video))
        }

        Window(
            onCloseRequest = {
                runBlocking { container.shutdown() }
                exitApplication()
            },
            state = windowState,
            title = "VideoPlayerSJ",
            icon = rememberVectorPainter(Icons.Rounded.PlayCircle),
            onPreviewKeyEvent = { event -> keyHandlerHost.handler?.invoke(event) ?: false },
        ) {
            remember { window.minimumSize = Dimension(900, 600) }

            VideoPlayerSJTheme {
                CompositionLocalProvider(LocalThumbnailLoader provides container.thumbnailLoader) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        AnimatedContent(
                            targetState = backStack.last(),
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                        ) { screen ->
                            when (screen) {
                                Screen.Library -> LibraryScreen(
                                    repository = container.libraryRepository,
                                    isVlcAvailable = container.playerController != null,
                                    onShowClick = { navigate(Screen.ShowDetail(it)) },
                                    onVideoClick = playVideo,
                                )

                                is Screen.ShowDetail -> ShowDetailScreen(
                                    showId = screen.showId,
                                    repository = container.libraryRepository,
                                    onBack = ::goBack,
                                    onVideoClick = playVideo,
                                )

                                is Screen.Player -> Surface(color = Color.Black) {
                                    PlayerScreen(
                                        initialVideo = screen.video,
                                        controller = container.playerController!!,
                                        repository = container.libraryRepository,
                                        keyHandlerHost = keyHandlerHost,
                                        isFullscreen = isFullscreen,
                                        onToggleFullscreen = { setFullscreen(!isFullscreen) },
                                        onBack = ::goBack,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
