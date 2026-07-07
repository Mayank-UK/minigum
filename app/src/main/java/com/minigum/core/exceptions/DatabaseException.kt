package com.minigum.core.exceptions

/** Local-persistence failures: read/write/delete/connection errors from the database layer. */
class DatabaseException(
    message: String = "Database error",
    cause: Throwable? = null,
    context: String? = null
) : AppException(message, cause, context) {
    companion object {
        fun readFailed(cause: Throwable? = null, context: String? = null) =
            DatabaseException("Failed to read from database", cause, context)

        fun writeFailed(cause: Throwable? = null, context: String? = null) =
            DatabaseException("Failed to write to database", cause, context)

        fun deleteFailed(cause: Throwable? = null, context: String? = null) =
            DatabaseException("Failed to delete from database", cause, context)

        fun connectionFailed(cause: Throwable? = null, context: String? = null) =
            DatabaseException("Database connection failed", cause, context)
    }
}
