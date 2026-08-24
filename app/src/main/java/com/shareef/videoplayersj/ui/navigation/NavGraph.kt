package com.shareef.videoplayersj.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shareef.videoplayersj.di.AppContainer
import com.shareef.videoplayersj.ui.library.LibraryScreen
import com.shareef.videoplayersj.ui.player.PlayerScreen
import com.shareef.videoplayersj.ui.player.components.MiniPlayer
import com.shareef.videoplayersj.ui.showdetail.ShowDetailScreen

@Composable
fun NavGraph(appContainer: AppContainer, navController: NavHostController = rememberNavController()) {
    val nowPlaying by appContainer.playbackConnection.nowPlaying.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val onPlayerRoute = currentBackStackEntry?.destination?.route == Routes.PLAYER

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
