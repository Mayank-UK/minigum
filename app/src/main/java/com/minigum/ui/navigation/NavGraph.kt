package com.minigum.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.minigum.ui.screens.home.HomeScreen
import com.minigum.ui.screens.pong.PongScreen
import com.minigum.ui.screens.shooter.ShooterScreen
import com.minigum.ui.screens.snake.SnakeScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = NavRouter.HomeScreen.route, modifier = modifier,) {
        composable(NavRouter.HomeScreen.route) { HomeScreen(navController = navController) }
        composable(NavRouter.SnakeScreen.route) { SnakeScreen(navController = navController) }
        composable(NavRouter.PongScreen.route) { PongScreen(navController = navController) }
        composable(NavRouter.ShooterScreen.route) { ShooterScreen(navController = navController) }
    }
}
