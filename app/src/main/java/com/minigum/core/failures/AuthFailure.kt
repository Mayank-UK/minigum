package com.minigum.core.failures

/**
 * The UI-facing form of [com.minigum.core.exceptions.AuthException]. The exception's specific
 * message (e.g. "An account already exists with these details.") is preserved verbatim — auth
 * errors need to tell the user exactly what went wrong, not a generic "server error".
 */
data class AuthFailure(
    override val message: String,
    override val severity: FailureSeverity = FailureSeverity.WARNING,
    override val cause: Failure? = null,
    override val technicalDetails: String? = null,
) : Failure() {
    override fun withCause(cause: Failure?): AuthFailure = copy(cause = cause)
}
