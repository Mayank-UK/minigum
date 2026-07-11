package com.minigum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minigum.ui.theme.ArcadeColors
import com.minigum.ui.theme.Sizes

/** One line of result info on the game-over screen (e.g. "Score" → "30"). */
data class GameStat(val label: String, val value: String, val highlight: Boolean = false)

/**
 * Reusable game-over overlay shared by every score-based mini-game (Snake, Pong, Shooter, Runner,
 * Flappy, Balloon, Sudoku). Replaces the per-screen `GameOverOverlay`/`CompleteOverlay` copies.
 *
 * The caller supplies a [title], a list of [stats], and the standard actions. An optional
 * [inlineLeaderboard] slot renders a live mini leaderboard beneath the stats (showing the player's
 * score in context), and [onFullLeaderboard] adds a button to the full leaderboard screen.
 */
@Composable
fun GameOverOverlay(
    title: String,
    stats: List<GameStat>,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = ArcadeColors.Magenta,
    playAgainText: String = "Play again",
    hapticOnShow: Boolean = false,
    inlineLeaderboard: (@Composable () -> Unit)? = null,
    onFullLeaderboard: (() -> Unit)? = null,
) {
    if (hapticOnShow) {
        val haptic = LocalHapticFeedback.current
        LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(Sizes.lg)
                .width(320.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Sizes.sm),
        ) {
            Text(
                title,
                color = accent,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )

            stats.forEach { stat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stat.label,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stat.value,
                        color = if (stat.highlight) ArcadeColors.Gold else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            inlineLeaderboard?.invoke()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Sizes.sm),
                verticalArrangement = Arrangement.spacedBy(Sizes.sm),
            ) {
                if (onFullLeaderboard != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            text = "Full leaderboard",
                            onClick = onFullLeaderboard,
                            type = ButtonType.Text,
                        )
                    }
                }
                Button(text = playAgainText, onClick = onPlayAgain)
                Button(text = "Exit", onClick = onExit,)
            }
        }
    }
}
