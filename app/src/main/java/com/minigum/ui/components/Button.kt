package com.minigum.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minigum.ui.theme.ArcadeColors
import com.minigum.ui.theme.Sizes

/**
 * The three button styles used across the app, mirroring the cloudberry `ButtonTypes`.
 * - [Filled]: solid — the primary call to action. Uses the signature purple→blue gradient.
 * - [Outlined]: transparent with a neon outline — secondary actions.
 * - [Text]: borderless text link — tertiary actions.
 */
enum class ButtonType { Filled, Outlined, Text }

/**
 * Minigum's standard button — a styled wrapper so the look (gradient fill, neon outline, arcade
 * radius) stays consistent everywhere. Mirrors cloudberry's `Button`.
 *
 * @param text     Button label.
 * @param onClick  Click handler; ignored when [loading] or [enabled] is false.
 * @param type     Visual style; defaults to [ButtonType.Filled].
 * @param loading  Shows a spinner and disables interaction (e.g. during a network call).
 * @param enabled  Greyed out and non-interactive when false.
 * @param small    Compact height for inline/toolbar use.
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.Filled,
    loading: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
    fullWidth: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val active = enabled && !loading
    val height = if (small) 36.dp else 52.dp
    val contentColor = when (type) {
        ButtonType.Filled -> Color.White
        ButtonType.Outlined -> if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ButtonType.Text -> MaterialTheme.colorScheme.primary
    }

    val boxModifier = modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .height(height)
        .then(if (type != ButtonType.Text) Modifier.clip(MaterialTheme.shapes.medium) else Modifier)
        .then(
            if (type == ButtonType.Filled) {
                Modifier.background(
                    if (active) Brush.linearGradient(ArcadeColors.PurpleGradient)
                    else Brush.linearGradient(
                        listOf(Color(0xFF6A11CB).copy(alpha = 0.35f), Color(0xFF2575FC).copy(alpha = 0.35f))
                    )
                )
            } else Modifier
        )
        .clickable(enabled = active, onClick = onClick)

    if (type == ButtonType.Outlined) {
        Surface(
            modifier = boxModifier,
            color = Color.Transparent,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(
                width = if (active) 2.dp else 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) { ButtonContent(text, loading, leadingIcon, contentColor) }
        return
    }

    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        ButtonContent(text, loading, leadingIcon, contentColor)
    }
}

@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
    leadingIcon: ImageVector?,
    contentColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.sm),
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = contentColor,
                modifier = Modifier.size(Sizes.md),
            )
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
