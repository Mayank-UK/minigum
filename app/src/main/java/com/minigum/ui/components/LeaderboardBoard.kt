package com.minigum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minigum.domain.models.LeaderboardEntry
import com.minigum.domain.models.LeaderboardGame
import com.minigum.domain.models.UserRank
import com.minigum.ui.theme.ArcadeColors
import com.minigum.ui.theme.Sizes

/**
 * Reusable ranked leaderboard list — the single source of truth for how a game's top scores render.
 *
 * Shared by the full [com.minigum.ui.screens.leaderboard.LeaderboardScreen] and the inline board
 * embedded in [GameOverOverlay], so the look is identical everywhere. Each row flags the current
 * user; [showFooter] toggles the "You are ranked #N" line (useful in the compact inline view).
 */
@Composable
fun LeaderboardBoard(
    game: LeaderboardGame,
    entries: List<LeaderboardEntry>,
    userRank: UserRank?,
    modifier: Modifier = Modifier,
    showFooter: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Sizes.xs)) {
        if (entries.isEmpty()) {
            Text(
                "No scores yet for ${game.displayName}. Be the first!",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            entries.forEach { entry -> LeaderboardRow(entry, game) }
        }

//        if (showFooter) {
//            userRank?.let { rank ->
//                Text(
//                    "You are ranked #${rank.rank} in ${game.displayName} " +
//                        "(best ${rank.bestScore} ${game.unit}).",
//                    fontWeight = FontWeight.Bold,
//                    style = MaterialTheme.typography.titleSmall,
//                    modifier = Modifier.padding(top = Sizes.sm),
//                    color = MaterialTheme.colorScheme.primary,
//                )
//            }
//        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, game: LeaderboardGame) {
    val highlight = entry.isCurrentUser
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Sizes.sm))
            .background(
                if (highlight) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .padding(Sizes.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.md),
    ) {
        // Rank medal for the podium, plain number otherwise.
        if (entry.rank <= 3) {
            val medal = when (entry.rank) {
                1 -> ArcadeColors.Gold
                2 -> Color(0xFFC0C0C0)
                else -> Color(0xFFCD7F32)
            }
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = medal,
                modifier = Modifier.width(Sizes.lg),
            )
        } else {
            Text(
                "#${entry.rank}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(Sizes.lg),
            )
        }

        Text(
            if (highlight) "${entry.displayName} (you)" else entry.displayName,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${entry.score} ${game.unit}",
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
