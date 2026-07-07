package com.minigum.core.failures

/**
 * The UI/domain layer's representation of an error.
 *
 * [com.minigum.core.exceptions.AppException]s are translated into [Failure]s by
 * [com.minigum.core.mapping.ExceptionMapper] before crossing into the domain/UI,
 * so the UI never sees throwables — only a user-facing [message], a [severity], and an optional
 * causal [chain] of failures.
 */
sealed class Failure {
    abstract val message: String
    abstract val severity: FailureSeverity
    abstract val cause: Failure?
    open val technicalDetails: String? = null

    /** Returns a copy of this failure with [cause] attached, so callers can build a cause chain. */
    abstract fun withCause(cause: Failure?): Failure

    val chain: String
        get() = buildString {
            append(message)
            var current: Failure? = cause
            while (current != null) {
                append(" → ").append(current.message)
                current = current.cause
            }
        }

    override fun toString(): String =
        if (cause != null) "$message (caused by: ${cause!!.message})" else message
}
