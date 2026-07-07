package com.minigum.core.failures

data class CacheFailure(
    override val message: String,
    override val severity: FailureSeverity = FailureSeverity.INFO,
    override val cause: Failure? = null,
    override val technicalDetails: String? = null
) : Failure() {
    override fun withCause(cause: Failure?): CacheFailure = copy(cause = cause)
    companion object {
        fun notFound(cause: Failure? = null) = CacheFailure(
            "No cached data available.",
            FailureSeverity.INFO,
            cause
        )

        fun writeFailed(cause: Failure? = null) = CacheFailure(
            "Failed to save data locally.",
            FailureSeverity.INFO,
            cause
        )

        fun readFailed(
            message: String = "Failed to read cached data.",
            severity: FailureSeverity = FailureSeverity.WARNING,
            cause: Failure? = null
        ) = CacheFailure(message, severity, cause)

        fun corrupted(cause: Failure? = null) = CacheFailure(
            "Cached data is corrupted.",
            FailureSeverity.WARNING,
            cause
        )
    }
}
