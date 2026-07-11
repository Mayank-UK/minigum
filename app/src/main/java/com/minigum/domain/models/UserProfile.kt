package com.minigum.domain.models

/**
 * The authenticated player's identity, exposed by [com.minigum.domain.repositoryinterfaces.AuthRepository].
 * Pure domain model — no Firebase types leak here.
 */
data class UserProfile(
    val uid: String,
    val email: String?,
    val displayName: String,
)
