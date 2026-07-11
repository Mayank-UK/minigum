package com.minigum.ui.screens.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minigum.domain.models.LeaderboardGame
import com.minigum.ui.components.LeaderboardBoard
import com.minigum.ui.theme.ArcadeColors
import com.minigum.ui.theme.Sizes

/**
 * A compact, self-contained leaderboard card meant to live *inside* a game-over overlay: it
 * submits the just-finished score and shows where the player landed among the top scores + their
 * global rank. This is separate from the full [LeaderboardScreen].
 *
 * Loads itself via [LeaderboardCardViewModel]; the host only supplies the [game] and [score].
 */
@Composable
fun InlineLeaderboard(
    game: LeaderboardGame,
    score: Int,
    modifier: Modifier = Modifier,
    onLogIn: (() -> Unit)? = null,
    viewModel: LeaderboardCardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(game, score) { viewModel.load(game, score) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Sizes.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Sizes.xs),
    ) {
        Text(
            "${game.displayName} leaderboard",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ArcadeColors.Magenta,
        )

        when (val s = state) {
            LeaderboardCardState.Loading -> Box(Modifier.fillMaxWidth().padding(Sizes.sm), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(Sizes.md))
            }
            LeaderboardCardState.NotLoggedIn -> Text(
                "Log in to save your score and compete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is LeaderboardCardState.Error -> Text(
                s.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            is LeaderboardCardState.Loaded -> LeaderboardBoard(
                game = s.game,
                entries = s.entries,
                userRank = s.userRank,
            )
        }
    }
}
