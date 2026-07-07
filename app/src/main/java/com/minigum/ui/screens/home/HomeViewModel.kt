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
            title = "Android fundamentals",
            image = R.drawable.snake,
            routePath = NavRouter.SnakeScreen.route
        ),
    )
}
