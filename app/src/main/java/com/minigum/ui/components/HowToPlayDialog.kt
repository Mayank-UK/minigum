package com.minigum.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minigum.ui.theme.Sizes

/**
 * A reusable "How to play" dialog shown when a game opens.
 *
 * Each game supplies its own [title] and [instructions] (dynamic content),
 * so this component can be shared across every mini-game in the app.
 *
 * Built on a plain [Dialog] + [Card] (not AlertDialog) so the padding and
 * margins are fully controlled and the look matches the game overlays.
 *
 * The dialog can only be dismissed by tapping **Continue** — tapping outside
 * or pressing Back does nothing, so the player must acknowledge the rules.
 *
 * @param title       Dialog title, e.g. "How to play — Snake".
 * @param instructions Ordered list of instruction lines rendered as a numbered list.
 * @param onContinue  Called when the player taps Continue (the only way to dismiss).
 */
@Composable
fun HowToPlayDialog(
    title: String,
    instructions: List<String>,
    onContinue: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* no-op: only Continue dismisses */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        // Cap the dialog height so there's always a margin from the top and
        // bottom of the screen (important in landscape). The whole body —
        // title, instructions, and the Continue button — scrolls together, so
        // the player reaches Continue by scrolling to the bottom.
        BoxWithConstraints {
            val maxHeightDp = maxHeight
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Sizes.md)
                    .heightIn(max = maxHeightDp * 0.85f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .padding(Sizes.lg)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Sizes.md),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    instructions.forEachIndexed { index, line ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(Sizes.sm),
                        ) {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // In flow at the end of the scrollable content.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onContinue) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
    }
}
