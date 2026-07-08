package com.minigum.ui.screens.home

import androidx.lifecycle.ViewModel
import com.minigum.domain.models.LinkData
import com.minigum.ui.navigation.NavRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.minigum.R

@HiltViewModel
class HomeViewModel @Inject constructor(
) : ViewModel() {

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
    )
}
