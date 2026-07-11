package com.minigum.core.exceptions

/**
 * Authentication failures: wrong password, unknown account, email already in use, weak password,
 * malformed email, etc.
 *
 * These are translated from Firebase's `FirebaseAuth*Exception` types in
 * [com.minigum.core.guards.guardedFirebaseCall]. Auth errors carry a **user-facing** [message] that
 * must reach the UI verbatim (e.g. "An account already exists with these details."), so they get
 * their own type rather than being folded into [ServerException] — whose mapper would otherwise
 * replace the specific message with a generic "Server error" (it keys off HTTP status codes, which
 * Firebase auth errors don't have).
 */
class AuthException(
    message: String,
    cause: Throwable? = null,
    context: String? = null,
) : AppException(message, cause, context)
