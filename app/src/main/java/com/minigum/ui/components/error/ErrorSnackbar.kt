package com.minigum.ui.components.error

import androidx.compose.foundation.clickable
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
 * Minimal non-blocking notice for INFO-level failures. Auto-dismisses after [dismissAfterMs]
 * and can be tapped to dismiss. Mirrors the iOS guide's `ErrorSnackbar`.
 */
@Composable
fun ErrorSnackbar(
    failure: Failure,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissAfterMs: Long = 3_000L
) {
    LaunchedEffect(failure) {
        delay(dismissAfterMs)
        onDismiss()
    }

    Surface(
        color = failure.severity.color,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onDismiss() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
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
            Spacer(Modifier.size(8.dp))
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.8f))
        }
    }
}
