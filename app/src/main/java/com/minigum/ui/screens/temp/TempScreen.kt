package com.minigum.ui.screens.temp

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.minigum.ui.components.PageScaffold


@Composable
fun TempScreen(
    navController: NavController,
    viewModel: TempViewModel = hiltViewModel(),
) {
    PageScaffold(
        navController = navController,
        title = "Android Developer's Guide",
        mobileUI = {
            Text(text = "Temp screen")
        }
    )
}

@Preview
@Composable
fun TempScreenPreview() {
    TempScreen(navController = rememberNavController())
}