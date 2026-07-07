package com.minigum.ui.components.error

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minigum.core.failures.Failure
import kotlinx.coroutines.delay

/**
 * Non-blocking banner pinned to the top, for WARNING-level failures. Auto-dismisses after
 * [dismissAfterMs] and can be dismissed manually. Mirrors the iOS guide's `ErrorBanner`.
 */
@Composable
fun ErrorBanner(
    failure: Failure,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismiss: Boolean = true,
    dismissAfterMs: Long = 5_000L
) {
    if (autoDismiss) {
        LaunchedEffect(failure) {
            delay(dismissAfterMs)
            onDismiss()
        }
    }

    Surface(
        color = failure.severity.color.copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = failure.severity.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = failure.message,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                failure.cause?.let { cause ->
                    Text(
                        text = "Caused by: ${cause.message}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White)
            }
        }
    }
}
