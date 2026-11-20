package com.streamline.app.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamline.app.core.di.AppContainer
import com.streamline.app.core.logging.AppLog
import com.streamline.app.feature.downloads.DownloadsRoute
import com.streamline.app.feature.favorites.FavoritesRoute
import com.streamline.app.feature.magnets.MagnetCatalogRoute
import com.streamline.app.feature.player.PlayerSessionRoute
import com.streamline.app.feature.settings.SettingsRoute

object Routes {
    const val MAGNETS = "magnets"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val FAVORITES = "favorites"
    const val PLAYER_SESSION = "player-session/{sessionId}"

    fun playerSession(sessionId: String): String = "player-session/" + sessionId
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun StreamlineNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val pendingMagnet by container.pendingMagnet.collectAsStateWithLifecycle()

    LaunchedEffect(pendingMagnet) {
        val request = pendingMagnet ?: return@LaunchedEffect
        container.pendingMagnet.value = null
        container.playbackSessionRepository.offer(request)
        navController.navigate(Routes.playerSession(java.util.UUID.randomUUID().toString()))
    }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, arguments ->
            AppLog.event(
                "NAV",
                "destination",
                fields = mapOf(
                    "route" to (destination.route ?: "unknown"),
                    "args" to arguments?.keySet()?.joinToString(),
                ),
            )
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.MAGNETS,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> fullWidth / 7 },
            ) + fadeIn(tween(310))
        },
        exitTransition = {
            fadeOut(tween(220))
        },
        popEnterTransition = {
            fadeIn(tween(280))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> fullWidth / 7 },
            ) + fadeOut(tween(240))
        },
    ) {
        composable(Routes.MAGNETS) {
            MagnetCatalogRoute(
                container = container,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                onOpenPlayer = { request ->
                    container.playbackSessionRepository.offer(request)
                    
                    
                    navController.navigate(Routes.playerSession(java.util.UUID.randomUUID().toString()))
                },
                onDownload = { request, selectedFiles ->
                    container.downloadManager.enqueue(request, selectedFiles)
                    navController.navigate(Routes.DOWNLOADS) {
                        launchSingleTop = true
                    }
                },
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { request ->
                    container.playbackSessionRepository.offer(request)
                    navController.navigate(Routes.playerSession(java.util.UUID.randomUUID().toString()))
                },
            )
        }

        composable(Routes.DOWNLOADS) {
            DownloadsRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenMagnets = { navController.navigate(Routes.MAGNETS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PLAYER_SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            enterTransition = { fadeIn(tween(320)) },
            exitTransition = { fadeOut(tween(260)) },
            popEnterTransition = { fadeIn(tween(280)) },
            popExitTransition = { fadeOut(tween(260)) },
        ) {
            PlayerSessionRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
