package com.minigum.core.failures

data class ServerFailure(
    override val message: String,
    override val severity: FailureSeverity = FailureSeverity.CRITICAL,
    override val cause: Failure? = null,
    override val technicalDetails: String? = null,
    val statusCode: Int? = null
) : Failure() {
    override fun withCause(cause: Failure?): ServerFailure = copy(cause = cause)
    companion object {
        fun internalServerError(
            message: String = "Server encountered an error. Please try again later.",
            severity: FailureSeverity = FailureSeverity.CRITICAL,
            statusCode: Int = 500,
            cause: Failure? = null
        ) = ServerFailure(message, severity, cause, null, statusCode)

        fun badRequest(cause: Failure? = null) = ServerFailure(
            "Invalid request. Please check your input.",
            FailureSeverity.WARNING,
            cause,
            null,
            400
        )

        fun unauthorized(cause: Failure? = null) = ServerFailure(
            "Authentication required. Please log in.",
            FailureSeverity.WARNING,
            cause,
            null,
            401
        )

        fun notFound(cause: Failure? = null) = ServerFailure(
            "Requested resource not found.",
            FailureSeverity.WARNING,
            cause,
            null,
            404
        )
    }
}
