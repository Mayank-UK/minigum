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
    object LoginScreen : NavRouter("login")
    object LeaderboardHubScreen : NavRouter("leaderboard_hub")
    object LeaderboardScreen : NavRouter("leaderboard?game={game}&score={score}")
}

/** Route argument names shared between the nav graph and the leaderboard entry points. */
object LeaderboardArgs {
    const val GAME = "game"
    const val SCORE = "score"
}

/**
 * Builds a leaderboard route for [game]. Pass a non-negative [score] to submit it as the player's
 * best (used from a game's game-over screen); omit it (or pass -1) to just view the board.
 */
fun leaderboardRoute(game: com.minigum.domain.models.LeaderboardGame, score: Int = -1): String =
    "leaderboard?game=${game.id}&score=$score"
