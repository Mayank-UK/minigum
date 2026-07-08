package com.minigum.ui.navigation

/**
 * Type-safe route definitions for the app's navigation graph.
 * Add a new [NavRouter] entry for each destination screen.
 */
sealed class NavRouter(val route: String) {
    object HomeScreen : NavRouter("home")
    object SnakeScreen : NavRouter("snake")
    object PongScreen : NavRouter("pong")
    object ShooterScreen : NavRouter("shooter")
}
