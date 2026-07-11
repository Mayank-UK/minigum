package com.minigum.core.validators

/**
 * Field validators, kept in one place (mirrors cloudberry's `core/validators.dart`).
 *
 * Each validator takes the field's current value and returns a user-facing error message, or `null`
 * when the value is valid. They are plain top-level functions so they can be passed directly to
 * [com.minigum.ui.components.TextField]'s `validator` slot.
 */

/** A validator maps an input string to an error message, or `null` when valid. */
typealias Validator = (String) -> String?

private val emailRegex = Regex(
    "^[^@\\s]+@([^@\\s]+\\.)+[^@\\s]{2,}$"
)

/** Email address shape check (used for login + sign-up). */
fun emailValidator(value: String): String? {
    if (value.isBlank()) return "Please enter your email address."
    if (!emailRegex.matches(value.trim())) return "Please enter a valid email address."
    return null
}

/**
 * Password rules.
 *
 * Firebase Auth requires at least 6 characters, so that's the hard floor enforced here. The stronger
 * regex (upper/lower/digit/symbol) is left available but disabled by default to match cloudberry's
 * intent — flip [strict] to turn it on app-wide.
 */
fun passwordValidator(value: String, strict: Boolean = false): String? {
    if (value.isBlank()) return "Password is required."
    if (value.length < 6) return "Password must be at least 6 characters."
    if (strict) {
        val strong = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")
        if (!strong.matches(value)) {
            return "Use 8+ chars with upper, lower and a digit."
        }
    }
    return null
}

/** Display name for sign-up (3–20 chars, letters/spaces only — keeps leaderboard rows tidy). */
fun nameValidator(value: String): String? {
    if (value.isBlank()) return "Please choose a display name."
    if (!Regex("^[a-zA-Z ]+$").matches(value)) return "Use letters and spaces only."
    if (value.trim().length < 3) return "Name must be at least 3 characters."
    if (value.trim().length > 20) return "Name must be less than 20 characters."
    return null
}

/** Must match the original password (for a confirm-password field). */
fun confirmPasswordValidator(value: String, original: String): String? {
    if (value.isBlank()) return "Please confirm your password."
    if (value != original) return "The passwords do not match."
    return null
}
