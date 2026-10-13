package com.shareef.videoplayersj.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.shareef.videoplayersj.desktop.input.GamepadButton
import com.shareef.videoplayersj.desktop.input.InputMode
import com.shareef.videoplayersj.desktop.model.LibraryVideo
import com.shareef.videoplayersj.desktop.torrent.SingleInstance
import com.shareef.videoplayersj.desktop.ui.common.LocalThumbnailLoader
import com.shareef.videoplayersj.desktop.ui.common.setTitleBarHidden
import com.shareef.videoplayersj.desktop.ui.downloads.AddTorrentDialog
import com.shareef.videoplayersj.desktop.ui.downloads.DownloadsScreen
import com.shareef.videoplayersj.desktop.ui.library.LibraryScreen
import com.shareef.videoplayersj.desktop.ui.library.LibraryUiState
import com.shareef.videoplayersj.desktop.ui.player.KeyHandlerHost
import com.shareef.videoplayersj.desktop.ui.player.PlayerScreen
import com.shareef.videoplayersj.desktop.ui.showdetail.ShowDetailScreen
import com.shareef.videoplayersj.desktop.ui.theme.VideoPlayerSJTheme
import com.shareef.videoplayersj.torrent.Magnet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.UIManager

private sealed interface Screen {
    data object Library : Screen
    data class ShowDetail(val showId: Long) : Screen
    data class Player(val video: LibraryVideo) : Screen
    data object Downloads : Screen
}

/** Starts in couch mode for this run only, e.g. from a Steam shortcut's launch options. */
private const val COUCH_FLAG = "--couch"

fun main(args: Array<String>) {
    // A magnet link clicked while the app is already open is handed to that window instead.
    if (!SingleInstance.claim(args.toList())) return
    // macOS delivers clicked links as an event rather than as arguments.
    runCatching {
        if (Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
            Desktop.getDesktop().setOpenURIHandler { event -> SingleInstance.deliver(event.uri.toString()) }
        }
    }
    // Gives the folder chooser the native Explorer/Finder look rather than Swing's default.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    InputMode.install()

    val container = AppContainer()
    val startInCouchMode = COUCH_FLAG in args || container.settingsStore.settings.couchMode

    application {
        val windowState = rememberWindowState(
            width = 1360.dp,
            height = 860.dp,
            position = WindowPosition(Alignment.Center),
            placement = if (startInCouchMode) WindowPlacement.Fullscreen else WindowPlacement.Floating,
        )
        val keyHandlerHost = remember { KeyHandlerHost() }
        val libraryUiState = remember { LibraryUiState() }
        var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Library)) }
        // Fullscreen is remembered as the placement to return to, since the window may have been maximized.
        var placementBeforeFullscreen by remember { mutableStateOf(WindowPlacement.Floating) }
        // Couch mode keeps the whole app fullscreen rather than just the player.
        var isCouchMode by remember { mutableStateOf(startInCouchMode) }
        // Magnet links waiting for the "where to save" prompt, oldest first.
        var pendingMagnets by remember { mutableStateOf(args.filter(Magnet::isMagnet)) }
        var downloadError by remember { mutableStateOf<String?>(null) }
        val activeDownloads by container.torrentEngine.downloads.collectAsState()

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

        fun toggleCouchMode() {
            isCouchMode = !isCouchMode
            container.settingsStore.update { it.copy(couchMode = isCouchMode) }
            setFullscreen(isCouchMode)
        }

        fun navigate(screen: Screen) {
            backStack = backStack + screen
        }

        fun goBack() {
            if (backStack.last() is Screen.Player && !isCouchMode) setFullscreen(false)
            if (backStack.size > 1) backStack = backStack.dropLast(1)
        }

        val playVideo: (LibraryVideo) -> Unit = { video ->
            if (container.playerController != null) navigate(Screen.Player(video))
        }

        fun defaultDownloadDir(): File {
            container.settingsStore.settings.lastDownloadDir?.let(::File)?.takeIf { it.isDirectory }?.let { return it }
            val home = File(System.getProperty("user.home"))
            return File(home, "Downloads").takeIf { it.isDirectory } ?: home
        }

        fun startDownload(magnet: String, dir: File) {
            pendingMagnets = pendingMagnets.drop(1)
            container.settingsStore.update { it.copy(lastDownloadDir = dir.absolutePath) }
            container.scope.launch {
                val result = withContext(Dispatchers.IO) { container.torrentEngine.add(magnet, dir) }
                result.exceptionOrNull()?.let { downloadError = it.message ?: it.toString() }
            }
            if (backStack.last() == Screen.Library) navigate(Screen.Downloads)
        }

        fun quit() {
            runBlocking { container.shutdown() }
            exitApplication()
        }

        Window(
            onCloseRequest = ::quit,
            state = windowState,
            title = "VideoPlayerSJ",
            icon = rememberVectorPainter(Icons.Rounded.PlayCircle),
            onPreviewKeyEvent = { event ->
                InputMode.onKeyOrGamepad()
                when {
                    keyHandlerHost.handler?.invoke(event) == true -> true
                    event.type == KeyEventType.KeyDown && event.key == Key.F11 -> {
                        setFullscreen(!isFullscreen)
                        true
                    }
                    else -> false
                }
            },
        ) {
            remember { window.minimumSize = Dimension(900, 600) }

            LaunchedEffect(isFullscreen) {
                withFrameNanos { }
                setTitleBarHidden(window, isFullscreen)
            }

            LaunchedEffect(Unit) {
                SingleInstance.messages.collect { message ->
                    if (windowState.isMinimized) windowState.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                    if (Magnet.isMagnet(message)) pendingMagnets = pendingMagnets + message.trim()
                }
            }

            // Controller buttons go to the player when it's open; elsewhere they act as the
            // equivalent keys, so the screens' keyboard navigation handles them.
            LaunchedEffect(Unit) {
                container.gamepad.presses.collect { button ->
                    if (!window.isFocused) return@collect
                    InputMode.onKeyOrGamepad()
                    if (keyHandlerHost.gamepadHandler?.invoke(button) == true) return@collect
                    val keyCode = button.keyCode() ?: return@collect
                    val target = window.mostRecentFocusOwner ?: window
                    for (id in listOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
                        target.dispatchEvent(KeyEvent(target, id, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED))
                    }
                }
            }

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
                                    uiState = libraryUiState,
                                    isVlcAvailable = container.playerController != null,
                                    isFullscreen = isFullscreen,
                                    isCouchMode = isCouchMode,
                                    onToggleCouchMode = ::toggleCouchMode,
                                    onQuit = ::quit,
                                    activeDownloads = activeDownloads.size,
                                    onOpenDownloads = { navigate(Screen.Downloads) },
                                    onShowClick = { navigate(Screen.ShowDetail(it)) },
                                    onVideoClick = playVideo,
                                )

                                is Screen.ShowDetail -> ShowDetailScreen(
                                    showId = screen.showId,
                                    repository = container.libraryRepository,
                                    onBack = ::goBack,
                                    onVideoClick = playVideo,
                                )

                                Screen.Downloads -> DownloadsScreen(
                                    engine = container.torrentEngine,
                                    libraryRepository = container.libraryRepository,
                                    onAddMagnet = { pendingMagnets = pendingMagnets + it },
                                    onBack = ::goBack,
                                )

                                is Screen.Player -> Surface(color = Color.Black) {
                                    PlayerScreen(
                                        initialVideo = screen.video,
                                        controller = container.playerController!!,
                                        repository = container.libraryRepository,
                                        keyHandlerHost = keyHandlerHost,
                                        isFullscreen = isFullscreen,
                                        escapeExitsFullscreen = !isCouchMode,
                                        onToggleFullscreen = { setFullscreen(!isFullscreen) },
                                        onBack = ::goBack,
                                    )
                                }
                            }
                        }
                    }

                    pendingMagnets.firstOrNull()?.let { magnet ->
                        AddTorrentDialog(
                            magnet = magnet,
                            initialDir = remember(magnet) { defaultDownloadDir() },
                            onStart = { dir -> startDownload(magnet, dir) },
                            onDismiss = { pendingMagnets = pendingMagnets.drop(1) },
                        )
                    }
                    downloadError?.let { error ->
                        AlertDialog(
                            onDismissRequest = { downloadError = null },
                            title = { Text("Couldn't start the download") },
                            text = { Text(error) },
                            confirmButton = { TextButton(onClick = { downloadError = null }) { Text("OK") } },
                        )
                    }
                }
            }
        }
    }
}

private fun GamepadButton.keyCode(): Int? = when (this) {
    GamepadButton.Up -> KeyEvent.VK_UP
    GamepadButton.Down -> KeyEvent.VK_DOWN
    GamepadButton.Left -> KeyEvent.VK_LEFT
    GamepadButton.Right -> KeyEvent.VK_RIGHT
    GamepadButton.A -> KeyEvent.VK_ENTER
    GamepadButton.B -> KeyEvent.VK_ESCAPE
    GamepadButton.Y -> KeyEvent.VK_CONTEXT_MENU
    GamepadButton.Start -> KeyEvent.VK_F11
    else -> null
}
