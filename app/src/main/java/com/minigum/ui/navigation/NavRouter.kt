package com.minigum.ui.navigation

/**
 * Type-safe route definitions for the app's navigation graph.
 * Add a new [NavRouter] entry for each destination screen.
 */
sealed class NavRouter(val route: String) {
    object WelcomeScreen : NavRouter("welcome")
    object HomeScreen : NavRouter("home")
    object SnakeScreen : NavRouter("snake")
    object PongScreen : NavRouter("pong")
    object ShooterScreen : NavRouter("shooter")
    object RunnerScreen : NavRouter("runner")
    object FlappyScreen : NavRouter("flappy")
    object SudokuScreen : NavRouter("sudoku")
    object ChessScreen : NavRouter("chess")
    object LudoScreen : NavRouter("ludo")
    object LogicScreen : NavRouter("logic")
    object BalloonScreen : NavRouter("balloon")
    object UserScreen : NavRouter("user")
    object AboutScreen :NavRouter("about")
    object GameLogicScreen : NavRouter("game_logic")
}
