package com.minigum.core.exceptions

/** Network-layer failures: no connectivity, timeouts, unreachable host, dropped connection. */
class NetworkException(
    message: String = "No internet connection",
    cause: Throwable? = null,
    context: String? = null
) : AppException(message, cause, context) {
    companion object {
        fun noConnection(cause: Throwable? = null, context: String? = null) =
            NetworkException("No internet connection available", cause, context)

        fun timeout(cause: Throwable? = null, context: String? = null) =
            NetworkException("Request timed out", cause, context)

        fun unreachable(cause: Throwable? = null, context: String? = null) =
            NetworkException("Server unreachable", cause, context)

        fun connectionLost(cause: Throwable? = null, context: String? = null) =
            NetworkException("Connection lost during request", cause, context)
    }
}
