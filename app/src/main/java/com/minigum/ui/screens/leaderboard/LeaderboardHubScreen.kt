package com.minigum.ui.screens.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.minigum.domain.models.LeaderboardGame
import com.minigum.ui.components.PageScaffold
import com.minigum.ui.navigation.leaderboardRoute
import com.minigum.ui.theme.Sizes

/**
 * The drawer's "Leaderboard" entry: a picker of the score-based games. Tapping one opens that
 * game's leaderboard (which itself gates on login).
 */
@Composable
fun LeaderboardHubScreen(navController: NavController) {
    PageScaffold(
        navController = navController,
        title = "Leaderboards",
        mobileUI = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Sizes.md),
                verticalArrangement = Arrangement.spacedBy(Sizes.sm),
            ) {
                Text(
                    "Pick a game to see its top players and your rank.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Plain Column (not LazyColumn): PageScaffold already scrolls vertically, and only
                // 7 games are listed — nesting a LazyColumn there would crash on infinite height.
                Column(verticalArrangement = Arrangement.spacedBy(Sizes.xs)) {
                    LeaderboardGame.entries.forEach { game ->
                        LeaderboardGameCard(game) {
                            navController.navigate(leaderboardRoute(game))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun LeaderboardGameCard(game: LeaderboardGame, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.md),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Sizes.sm))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(Sizes.sm),
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.lg + Sizes.md)
                .clip(RoundedCornerShape(Sizes.sm))
                .background(
                    Brush.linearGradient(listOf(Color(0xFFFFD400), Color(0xFFFF6D00)))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(game.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Ranked by ${if (game.higherIsBetter) "highest" else "fastest"} ${game.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
