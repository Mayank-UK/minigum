package com.minigum.ui.screens.gamelogic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.minigum.ui.components.PageScaffold
import com.minigum.ui.theme.MinigumTheme
import com.minigum.ui.theme.Sizes

/** Accent gradient per game, in the same order as [gameDocs]. */
private val gameAccents = listOf(
    listOf(Color(0xFF66BB6A), Color(0xFF2E7D32)), // Snake — green
    listOf(Color(0xFF42A5F5), Color(0xFF1565C0)), // Pong — blue
    listOf(Color(0xFFFFCA28), Color(0xFFFF8F00)), // Flappy — amber
    listOf(Color(0xFF8D6E63), Color(0xFF4E342E)), // Chess — wood
    listOf(Color(0xFFEF5350), Color(0xFFC62828)), // Ludo — red
    listOf(Color(0xFF26A69A), Color(0xFF00695C)), // Sudoku — teal
    listOf(Color(0xFF7E57C2), Color(0xFF4527A0)), // Logic — purple
    listOf(Color(0xFFEC407A), Color(0xFFAD1457)), // Shooter — magenta
    listOf(Color(0xFFFF7043), Color(0xFFD84315)), // Runner — orange
    listOf(Color(0xFF26C6DA), Color(0xFF00838F)), // Balloon — cyan
)

@Composable
fun GameLogicScreen(
    navController: NavController,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    PageScaffold(
        navController = navController,
        title = "Game logic",
        mobileUI = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Sizes.md),
                verticalArrangement = Arrangement.spacedBy(Sizes.md),
            ) {
                Text(
                    text = "How each game really works — algorithms, AI and the exact sources of randomness, taken straight from the source code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                gameDocs.forEachIndexed { index, doc ->
                    GameLogicCard(
                        doc = doc,
                        accent = gameAccents[index.coerceAtMost(gameAccents.lastIndex)],
                        expanded = expanded[doc.title] ?: false,
                        onToggle = { expanded[doc.title] = !(expanded[doc.title] ?: false) },
                    )
                }

                Spacer(Modifier.size(Sizes.sm))
            }
        },
    )
}

@Composable
private fun GameLogicCard(
    doc: GameDoc,
    accent: List<Color>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Sizes.md))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(Sizes.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        // Header row: gradient emoji chip + title/tagline + chevron.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(Sizes.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Sizes.sm))
                    .background(Brush.linearGradient(accent)),
                contentAlignment = Alignment.Center,
            ) {
                Text(doc.emoji, fontSize = 22.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    doc.tagline,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Sizes.md, end = Sizes.md, bottom = Sizes.md),
                verticalArrangement = Arrangement.spacedBy(Sizes.sm),
            ) {
                doc.sections.forEach { section ->
                    SectionBlock(section = section, accent = accent.first())
                }
            }
        }
    }
}

@Composable
private fun SectionBlock(section: GameSection, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(Sizes.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sizes.xs)) {
            Box(
                modifier = Modifier
                    .size(Sizes.sm)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                section.heading,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            section.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        if (section.bullets.isNotEmpty()) {
            section.bullets.forEach { bullet ->
                Row(
                    modifier = Modifier.padding(start = Sizes.sm),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Sizes.xs),
                ) {
                    Text("•", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        bullet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GameLogicScreenPreview() {
    MinigumTheme { GameLogicScreen(navController = rememberNavController()) }
}
