package com.minigum.data.remote.dtos

import com.google.firebase.auth.FirebaseUser
import com.minigum.domain.models.UserProfile

/**
 * Wire representation of a Firebase Auth user. Carries only the fields the app needs; the mapping
 * to the domain [UserProfile] lives in [toDomain].
 */
data class UserDto(
    val uid: String,
    val email: String?,
    val displayName: String,
)

fun FirebaseUser?.toUserDto(fallbackDisplayName: String): UserDto? {
    if (this == null) return null
    val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackDisplayName
    return UserDto(uid = uid, email = email, displayName = name)
}

fun UserDto.toDomain(): UserProfile = UserProfile(uid = uid, email = email, displayName = displayName)
