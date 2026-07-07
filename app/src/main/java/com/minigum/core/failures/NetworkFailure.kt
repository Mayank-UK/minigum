package com.minigum.core.failures

data class NetworkFailure(
    override val message: String,
    override val severity: FailureSeverity = FailureSeverity.WARNING,
    override val cause: Failure? = null,
    override val technicalDetails: String? = null
) : Failure() {
    override fun withCause(cause: Failure?): NetworkFailure = copy(cause = cause)
    companion object {
        fun noConnection(cause: Failure? = null) = NetworkFailure(
            "No internet connection. Please check your network settings.",
            FailureSeverity.WARNING,
            cause
        )

        fun timeout(cause: Failure? = null) = NetworkFailure(
            "Request timed out. Please try again.",
            FailureSeverity.WARNING,
            cause
        )

        fun unreachable(cause: Failure? = null) = NetworkFailure(
            "Server is unreachable. Please check your connection.",
            FailureSeverity.WARNING,
            cause
        )
    }
}
