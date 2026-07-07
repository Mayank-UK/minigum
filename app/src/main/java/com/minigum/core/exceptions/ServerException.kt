package com.minigum.core.exceptions

/** Server-side failures: non-2xx HTTP responses, bad request, unauthorized, not found, 5xx. */
class ServerException(
    message: String = "Server error",
    cause: Throwable? = null,
    context: String? = null,
    val statusCode: Int? = null
) : AppException(message, cause, context) {
    companion object {
        fun internalServerError(statusCode: Int = 500, cause: Throwable? = null, context: String? = null) =
            ServerException("Internal server error", cause, context, statusCode)

        fun badRequest(statusCode: Int = 400, cause: Throwable? = null, context: String? = null) =
            ServerException("Bad request", cause, context, statusCode)

        fun unauthorized(statusCode: Int = 401, cause: Throwable? = null, context: String? = null) =
            ServerException("Unauthorized", cause, context, statusCode)

        fun notFound(statusCode: Int = 404, cause: Throwable? = null, context: String? = null) =
            ServerException("Resource not found", cause, context, statusCode)
    }
}
