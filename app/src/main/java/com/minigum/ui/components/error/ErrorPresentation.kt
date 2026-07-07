package com.minigum.ui.components.error

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.minigum.core.failures.Failure
import com.minigum.core.failures.FailureSeverity

/** How a [Failure] should be presented, derived from its [FailureSeverity]. */
enum class ErrorPresentation {
    /** Blocking, full-screen — user can't proceed without acting (e.g. retry). */
    FULL_SCREEN,

    /** Non-blocking, pinned to the top — user can keep using the app. */
    BANNER,

    /** Non-blocking, minimal, bottom — transient informational notice. */
    SNACKBAR
}

/** Maps a [Failure] to a presentation based on severity (mirrors the iOS guide's routing). */
val Failure.presentation: ErrorPresentation
    get() = when (severity) {
        FailureSeverity.CRITICAL -> ErrorPresentation.FULL_SCREEN
        FailureSeverity.WARNING -> ErrorPresentation.BANNER
        FailureSeverity.INFO -> ErrorPresentation.SNACKBAR
    }

/** Severity → brand-neutral color (swap for theme colors if desired). */
val FailureSeverity.color: Color
    get() = when (this) {
        FailureSeverity.INFO -> Color(0xFF2196F3)   // blue
        FailureSeverity.WARNING -> Color(0xFFFF9800) // orange
        FailureSeverity.CRITICAL -> Color(0xFFF44336) // red
    }

/** Severity → icon. */
val FailureSeverity.icon: ImageVector
    get() = when (this) {
        FailureSeverity.INFO -> Icons.Filled.Info
        FailureSeverity.WARNING -> Icons.Filled.Warning
        FailureSeverity.CRITICAL -> Icons.Filled.Error
    }

/** Severity → user-facing title. */
val FailureSeverity.title: String get() = displayName
