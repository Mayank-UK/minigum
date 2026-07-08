package com.minigum.ui.screens.home

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.minigum.ui.theme.Sizes
import kotlin.unaryMinus

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    // can't use pageScaffold here as this screen contains LazyVerticalGrid at root
    Scaffold { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Sizes.md),
        ) {
            items(viewModel.links) { link ->
                Column(
                    modifier = Modifier
                        .padding(Sizes.sm)
                        .clickable {
                            navController.navigate(link.routePath)
                        }
                ) {
                    GameLink(
                        image = link.image,
                        onClick = {
                            navController.navigate(link.routePath)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GameLink(
    @DrawableRes image: Int,
    onClick: () -> Unit,
) {
    Image(
        contentDescription = "Snake image",
        painter = painterResource(image),
        modifier = Modifier
            .clip(RoundedCornerShape(Sizes.md))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Fit,
    )
}