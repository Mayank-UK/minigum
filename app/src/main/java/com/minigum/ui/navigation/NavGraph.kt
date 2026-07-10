package com.minigum.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.minigum.ui.screens.flappy.FlappyScreen
import com.minigum.ui.screens.home.HomeScreen
import com.minigum.ui.screens.welcome.WelcomeScreen
import com.minigum.ui.screens.balloon.BalloonScreen
import com.minigum.ui.screens.about.AboutScreen
import com.minigum.ui.screens.gamelogic.GameLogicScreen
import com.minigum.ui.screens.pong.PongScreen
import com.minigum.ui.screens.runner.RunnerScreen
import com.minigum.ui.screens.shooter.ShooterScreen
import com.minigum.ui.screens.snake.SnakeScreen
import com.minigum.ui.screens.user.UserScreen
import com.minigum.ui.screens.chess.ChessScreen
import com.minigum.ui.screens.ludo.LudoScreen
import com.minigum.ui.screens.logic.LogicScreen
import com.minigum.ui.screens.sudoku.SudokuScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = NavRouter.WelcomeScreen.route, modifier = modifier,) {
        composable(NavRouter.WelcomeScreen.route) { WelcomeScreen(navController = navController) }
        composable(NavRouter.HomeScreen.route) { HomeScreen(navController = navController) }
        composable(NavRouter.SnakeScreen.route) { SnakeScreen(navController = navController) }
        composable(NavRouter.PongScreen.route) { PongScreen(navController = navController) }
        composable(NavRouter.ShooterScreen.route) { ShooterScreen(navController = navController) }
        composable(NavRouter.RunnerScreen.route) { RunnerScreen(navController = navController) }
        composable(NavRouter.FlappyScreen.route) { FlappyScreen(navController = navController) }
        composable(NavRouter.SudokuScreen.route) { SudokuScreen(navController = navController) }
        composable(NavRouter.ChessScreen.route) { ChessScreen(navController = navController) }
        composable(NavRouter.LudoScreen.route) { LudoScreen(navController = navController) }
        composable(NavRouter.LogicScreen.route) { LogicScreen(navController = navController) }
        composable(NavRouter.BalloonScreen.route) { BalloonScreen(navController = navController) }
        composable(NavRouter.UserScreen.route) { UserScreen(navController = navController) }
        composable(NavRouter.AboutScreen.route) { AboutScreen(navController = navController) }
        composable(NavRouter.GameLogicScreen.route) { GameLogicScreen(navController = navController) }
    }
}
