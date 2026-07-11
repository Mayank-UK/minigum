package com.minigum.ui.screens.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.minigum.ui.components.LeaderboardBoard
import com.minigum.ui.navigation.NavRouter
import com.minigum.ui.theme.Sizes

/**
 * Shows one game's top-10 and the current user's global rank. Opened with a [gameId] and an
 * optional [pendingScore] (>= 0 submits it as the player's best first). Redirects to login when
 * the user is not signed in.
 *
 * Uses a bounded [Scaffold] (not [com.minigum.ui.components.PageScaffold]) so the loading spinner
 * can truly center in the viewport; the ranked list itself is rendered by the shared
 * [LeaderboardBoard] component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    navController: NavController,
    gameId: String,
    pendingScore: Int,
    viewModel: LeaderboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Auth gate: bounce to login, popping this screen so back from login returns to the game/home.
    LaunchedEffect(state) {
        if (state is LeaderboardUiState.RequiresLogin) {
            navController.navigate(NavRouter.LoginScreen.route) {
                popUpTo(NavRouter.LeaderboardScreen.route) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                LeaderboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                LeaderboardUiState.RequiresLogin -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Please log in to view the leaderboard.")
                }
                is LeaderboardUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
                is LeaderboardUiState.Loaded -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(Sizes.md),
                    verticalArrangement = Arrangement.spacedBy(Sizes.sm),
                ) {
                    if (s.justSetRecord) {
                        Text(
                            "🎉 New personal best!",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    LeaderboardBoard(
                        game = s.game,
                        entries = s.entries,
                        userRank = s.userRank,
                    )
                }
            }
        }
    }
}
