package com.shareef.videoplayersj.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shareef.videoplayersj.MainActivity
import com.shareef.videoplayersj.di.AppContainer
import com.shareef.videoplayersj.ui.downloads.AddTorrentDialog
import com.shareef.videoplayersj.ui.downloads.DownloadsScreen
import com.shareef.videoplayersj.ui.library.LibraryScreen
import com.shareef.videoplayersj.ui.player.PlayerScreen
import com.shareef.videoplayersj.ui.player.components.MiniPlayer
import com.shareef.videoplayersj.ui.showdetail.ShowDetailScreen
import com.shareef.videoplayersj.ui.transfer.IncomingFilesDialog
import com.shareef.videoplayersj.ui.transfer.TransferScreen

@Composable
fun NavGraph(
    appContainer: AppContainer,
    activity: MainActivity,
    navController: NavHostController = rememberNavController(),
) {
    val nowPlaying by appContainer.playbackConnection.nowPlaying.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val onPlayerRoute = currentBackStackEntry?.destination?.route == Routes.PLAYER
    val pendingMagnets by activity.pendingMagnets.collectAsState()
    val openDownloadsRequests by activity.openDownloadsRequests.collectAsState()
    val incomingOffers by appContainer.localTransfer.offers.collectAsState()
    val context = LocalContext.current

    fun openDownloads() {
        if (currentBackStackEntry?.destination?.route != Routes.DOWNLOADS) {
            navController.navigate(Routes.DOWNLOADS) { launchSingleTop = true }
        }
    }

    LaunchedEffect(openDownloadsRequests) {
        if (openDownloadsRequests > 0) openDownloads()
    }

    pendingMagnets.firstOrNull()?.let { magnet ->
        AddTorrentDialog(
            magnet = magnet,
            initialDir = remember(magnet) { appContainer.lastDownloadDir },
            onStart = { dir ->
                activity.dismissPendingMagnet()
                appContainer.startDownload(magnet, dir) { result ->
                    result.exceptionOrNull()?.let {
                        Toast.makeText(context, it.message ?: "Couldn't start the download", Toast.LENGTH_LONG).show()
                    }
                }
                if (!onPlayerRoute) openDownloads()
            },
            onDismiss = { activity.dismissPendingMagnet() },
        )
    }

    if (pendingMagnets.isEmpty()) {
        incomingOffers.firstOrNull()?.let { offer ->
            IncomingFilesDialog(
                offer = offer,
                initialDir = remember(offer.id) { appContainer.lastDownloadDir },
                onAccept = { dir ->
                    appContainer.lastDownloadDir = dir
                    appContainer.localTransfer.accept(offer.id, dir)
                    if (currentBackStackEntry?.destination?.route == Routes.LIBRARY) {
                        navController.navigate(Routes.TRANSFER) { launchSingleTop = true }
                    }
                },
                onDecline = { appContainer.localTransfer.decline(offer.id) },
            )
        }
    }

    Scaffold(
        bottomBar = {
            val np = nowPlaying
            if (np != null && !onPlayerRoute) {
                MiniPlayer(
                    title = np.title,
                    subtitle = np.subtitle,
                    isPlaying = np.isPlaying,
                    onClick = {
                        navController.navigate(Routes.player(np.videoId)) { launchSingleTop = true }
                    },
                    onPlayPause = { appContainer.playbackConnection.togglePlayPause() },
                    onDismiss = { appContainer.playbackConnection.stopAndDismiss() },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    appContainer = appContainer,
                    onOpenDownloads = { openDownloads() },
                    onOpenTransfer = { navController.navigate(Routes.TRANSFER) { launchSingleTop = true } },
                    onShowClick = { showId -> navController.navigate(Routes.showDetail(showId)) },
                    onVideoClick = { videoId -> navController.navigate(Routes.player(videoId)) },
                )
            }
            composable(
                route = Routes.SHOW_DETAIL,
                arguments = listOf(navArgument("showId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val showId = backStackEntry.arguments?.getLong("showId") ?: return@composable
                ShowDetailScreen(
                    showId = showId,
                    appContainer = appContainer,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { videoId -> navController.navigate(Routes.player(videoId)) },
                )
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(
                    appContainer = appContainer,
                    onAddMagnet = activity::addPendingMagnet,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.TRANSFER) {
                TransferScreen(appContainer = appContainer, onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("videoId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getLong("videoId") ?: return@composable
                PlayerScreen(
                    videoId = videoId,
                    appContainer = appContainer,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
