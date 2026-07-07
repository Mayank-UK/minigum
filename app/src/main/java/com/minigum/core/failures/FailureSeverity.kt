package com.minigum.core.failures

/** How serious a [Failure] is — drives UI presentation (icon, tone, retry behaviour). */
enum class FailureSeverity(val displayName: String) {
    INFO("Info"),
    WARNING("Warning"),
    CRITICAL("Error")
}
