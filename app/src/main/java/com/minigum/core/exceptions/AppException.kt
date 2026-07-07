package com.minigum.core.exceptions

/**
 * Base type for every error thrown by the data layer.
 *
 * Library-specific errors (Retrofit, OkHttp, Gson, Room) are translated into [AppException]
 * subclasses at the data boundary (see [com.minigum.core.guards.guardedApiCall]),
 * so platform types never leak into the domain or UI layers.
 *
 * Each exception carries:
 *  - [message]: a technical, developer-facing description.
 *  - [cause]:   the original throwable it was mapped from (preserves the chain).
 *  - [context]: optional extra info about where/why it happened.
 */
sealed class AppException(
    message: String,
    cause: Throwable? = null,
    val context: String? = null
) : Throwable(message, cause) {

    // AppException always carries a non-null message (narrowing override of Throwable.message).
    override val message: String = message

    /** Full causal chain as a human-readable string (this → caused by → ...). */
    val chain: String
        get() = buildString {
            append(message)
            var current: Throwable? = cause
            while (current != null) {
                append(" → ").append(current.message ?: current.toString())
                current = (current as? AppException)?.cause
            }
        }

    override fun toString(): String {
        val ctx = context?.let { " | Context: $it" } ?: ""
        val cse = cause?.let { " | Caused by: ${it.message ?: it}" } ?: ""
        return "${this::class.simpleName}: $message$ctx$cse"
    }
}
