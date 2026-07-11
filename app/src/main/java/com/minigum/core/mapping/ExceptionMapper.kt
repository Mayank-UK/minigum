package com.minigum.core.mapping

import com.minigum.core.exceptions.AppException
import com.minigum.core.exceptions.AuthException
import com.minigum.core.exceptions.DatabaseException
import com.minigum.core.exceptions.DecodingException
import com.minigum.core.exceptions.NetworkException
import com.minigum.core.exceptions.ServerException
import com.minigum.core.failures.AuthFailure
import com.minigum.core.failures.CacheFailure
import com.minigum.core.failures.Failure
import com.minigum.core.failures.FailureSeverity
import com.minigum.core.failures.NetworkFailure
import com.minigum.core.failures.ServerFailure

/**
 * Translates data-layer [AppException]s into domain/UI-layer [Failure]s.
 *
 * This is the single boundary where technical messages are converted into user-friendly ones
 * and where [FailureSeverity] is decided (e.g. 5xx → critical, 4xx → warning, cache → info).
 */
object ExceptionMapper {

    fun map(exception: AppException): Failure {
        val innerCause: Failure? = (exception.cause as? AppException)?.let { map(it) }
        return when (exception) {
            is AuthException -> mapAuth(exception, innerCause)
            is NetworkException -> mapNetwork(exception, innerCause)
            is ServerException -> mapServer(exception, innerCause)
            is DatabaseException -> mapDatabase(exception, innerCause)
            is DecodingException -> mapDecoding(exception, innerCause)
        }
    }

    /**
     * Auth errors carry an already-user-facing message (set in [com.minigum.core.guards.guardedFirebaseCall]),
     * so it's passed through unchanged at WARNING severity — never replaced with generic text.
     */
    private fun mapAuth(exception: AuthException, cause: Failure?): AuthFailure = AuthFailure(
        exception.message,
        FailureSeverity.WARNING,
        cause,
        exception.toString(),
    )

    private fun mapNetwork(exception: NetworkException, cause: Failure?): NetworkFailure =
        NetworkFailure(
            mapNetworkMessage(exception.message),
            FailureSeverity.WARNING,
            cause,
            exception.toString()
        )

    private fun mapServer(exception: ServerException, cause: Failure?): ServerFailure {
        val severity = exception.statusCode
            ?.let { if (it in 500..599) FailureSeverity.CRITICAL else FailureSeverity.WARNING }
            ?: FailureSeverity.CRITICAL
        return ServerFailure(
            mapServerMessage(exception.message, exception.statusCode),
            severity,
            cause,
            exception.toString(),
            exception.statusCode
        )
    }

    private fun mapDatabase(exception: DatabaseException, cause: Failure?): CacheFailure =
        CacheFailure(
            mapDatabaseMessage(exception.message),
            FailureSeverity.INFO,
            cause,
            exception.toString()
        )

    private fun mapDecoding(exception: DecodingException, cause: Failure?): ServerFailure =
        ServerFailure(
            "Server returned invalid data format",
            FailureSeverity.WARNING,
            cause,
            exception.toString(),
            null
        )

    private fun mapNetworkMessage(technicalMessage: String): String = when {
        technicalMessage.contains("timed", ignoreCase = true) ->
            "Request timed out. Please check your connection and try again."
        technicalMessage.contains("unreachable", ignoreCase = true) ->
            "Server is unreachable. Please check your internet connection."
        technicalMessage.contains("connection", ignoreCase = true) ->
            "Network connection issue. Please check your internet connection."
        else -> "Network error occurred. Please check your connection and try again."
    }

    private fun mapServerMessage(technicalMessage: String, statusCode: Int?): String {
        statusCode?.let { code ->
            return when (code) {
                400 -> "Invalid request. Please check your input and try again."
                401 -> "Authentication required. Please log in and try again."
                403 -> "You don't have permission to access this resource."
                404 -> "The requested resource was not found."
                500 -> "Server error occurred. Please try again later."
                503 -> "Service temporarily unavailable. Please try again later."
                else -> if (code in 400..499) "Client error occurred. Please check your request."
                else "Server error occurred. Please try again later."
            }
        }
        return when {
            technicalMessage.contains("Internal server error", ignoreCase = true) ->
                "Server encountered an error. Please try again later."
            technicalMessage.contains("Bad request", ignoreCase = true) ->
                "Invalid request. Please check your input."
            technicalMessage.contains("Unauthorized", ignoreCase = true) ->
                "Authentication required. Please log in."
            else -> "Server error occurred. Please try again later."
        }
    }

    private fun mapDatabaseMessage(technicalMessage: String): String = when {
        technicalMessage.contains("read", ignoreCase = true) -> "Failed to read saved data."
        technicalMessage.contains("write", ignoreCase = true) -> "Failed to save data locally."
        technicalMessage.contains("delete", ignoreCase = true) -> "Failed to remove saved data."
        technicalMessage.contains("connection", ignoreCase = true) -> "Local database connection issue."
        else -> "Local data storage issue occurred."
    }
}

/** Convenience for mapping any throwable into a [Failure]. */
fun Throwable.toFailure(): Failure =
    if (this is AppException) ExceptionMapper.map(this)
    else ServerFailure(
        "An unexpected error occurred",
        FailureSeverity.CRITICAL,
        null,
        this.message ?: toString(),
        null
    )
