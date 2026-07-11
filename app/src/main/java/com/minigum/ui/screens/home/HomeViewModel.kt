package com.minigum.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minigum.domain.models.LinkData
import com.minigum.domain.session.UserSession
import com.minigum.ui.navigation.NavRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.minigum.R

@HiltViewModel
class HomeViewModel @Inject constructor(
    userSession: UserSession,
) : ViewModel() {

    /** Name shown in the drawer: the signed-in user's name, or a stable guest name when signed out. */
    val displayName: StateFlow<String> = userSession.user
        .map { it?.displayName ?: userSession.guestDisplayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), userSession.guestDisplayName)

    /** Whether a real account is currently signed in (drives the drawer's Login button vs profile). */
    val isLoggedIn: StateFlow<Boolean> = userSession.user
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val links = listOf(
        LinkData(
            title = "Snake",
            image = R.drawable.snake,
            routePath = NavRouter.SnakeScreen.route
        ),
        LinkData(
            title = "Pong",
            image = R.drawable.pong,
            routePath = NavRouter.PongScreen.route
        ),
        LinkData(
            title = "Shooter",
            image = R.drawable.shooter,
            routePath = NavRouter.ShooterScreen.route
        ),
        LinkData(
            title = "Runner",
            image = R.drawable.runner,
            routePath = NavRouter.RunnerScreen.route
        ),
        LinkData(
            title = "Flappy",
            image = R.drawable.flappy,
            routePath = NavRouter.FlappyScreen.route
        ),
        LinkData(
            title = "Sudoku",
            image = R.drawable.sudoku,
            routePath = NavRouter.SudokuScreen.route
        ),
        LinkData(
            title = "Chess",
            image = R.drawable.chess,
            routePath = NavRouter.ChessScreen.route
        ),
        LinkData(
            title = "Ludo",
            image = R.drawable.ludo,
            routePath = NavRouter.LudoScreen.route
        ),
        LinkData(
            title = "Logic",
            image = R.drawable.logic,
            routePath = NavRouter.LogicScreen.route
        ),
        LinkData(
            title = "Balloon",
            image = R.drawable.balloon,
            routePath = NavRouter.BalloonScreen.route
        ),
    )
}
