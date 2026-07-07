package com.minigum.core.exceptions

/** Response-parsing failures: malformed JSON, missing fields, type mismatches. */
class DecodingException(
    message: String = "Failed to decode JSON",
    cause: Throwable? = null,
    context: String? = null,
    val decodingPath: List<String>? = null
) : AppException(message, cause, context) {
    companion object {
        fun jsonDecodingFailed(cause: Throwable? = null, context: String? = null) =
            DecodingException("Failed to decode JSON", cause, context)

        fun missingField(fieldName: String, cause: Throwable? = null, context: String? = null) =
            DecodingException("Missing required field: $fieldName", cause, context, listOf(fieldName))

        fun invalidType(
            fieldName: String,
            expectedType: String,
            cause: Throwable? = null,
            context: String? = null
        ) = DecodingException(
            "Invalid type for field: $fieldName (expected: $expectedType)",
            cause,
            context,
            listOf(fieldName)
        )
    }
}
