package com.minigum.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.minigum.ui.components.PageScaffold
import com.minigum.ui.theme.MinigumTheme
import com.minigum.ui.theme.Sizes

@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    PageScaffold(
        navController = navController,
        title = "About",
        mobileUI = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Sizes.lg),
                verticalArrangement = Arrangement.spacedBy(Sizes.md),
            ) {
                Text(
                    text = "Minigum",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "A pocket arcade of tiny games — classic arcade titles, board games, and bite-size logic puzzles, all in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Tap a game on the home grid to play. Use the profile icon in the top bar to jump between pages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    MinigumTheme { AboutScreen(navController = rememberNavController()) }
}
