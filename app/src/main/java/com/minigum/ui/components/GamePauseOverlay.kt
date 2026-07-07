package com.minigum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minigum.ui.theme.Sizes

/**
 * A reusable pause overlay shared across all mini-games.
 *
 * Renders the standard pause actions every game has — [onResume], [onHowToPlay],
 * [onExit] — and offers an optional [settings] slot for game-specific options
 * (e.g. Snake's D-pad toggle, a sound switch, difficulty, etc.).
 *
 * The component is pure UI: the owning screen supplies the callbacks and wires
 * them to its ViewModel / navigation.
 *
 * @param title       Heading shown at the top. Defaults to "Paused".
 * @param settings    Optional composable rendered between the heading and the
 *                    action buttons, for game-specific settings rows.
 * @param onResume    Called when the player taps Resume.
 * @param onHowToPlay Called when the player taps How to play.
 * @param onExit      Called when the player taps Exit to home.
 */
@Composable
fun GamePauseOverlay(
    onResume: () -> Unit,
    onHowToPlay: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Paused",
    resumeLabel: String = "Resume",
    exitLabel: String = "Exit to home",
    settings: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .padding(Sizes.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            settings?.invoke()

            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Text(resumeLabel)
            }
            Button(onClick = onHowToPlay, modifier = Modifier.fillMaxWidth()) {
                Text("How to play")
            }
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text(exitLabel)
            }
        }
    }
}
