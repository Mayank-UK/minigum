package com.minigum.domain.session

import com.minigum.domain.models.UserProfile
import com.minigum.domain.repositoryinterfaces.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the player's identity for the current app session and exposes it as a live stream so the
 * drawer, profile screen, and leaderboard gating all react to sign-in / sign-out.
 *
 * - Signed in: [user] emits the real [UserProfile] (and [guestDisplayName] is unused).
 * - Signed out: [user] emits `null`; callers fall back to [guestDisplayName] so the drawer still
 *   shows a friendly name — preserving the pre-Firebase random-name behaviour.
 *
 * Injected anywhere identity is needed, so every screen agrees on who the player is.
 */
@Singleton
class UserSession @Inject constructor(
    private val authRepository: AuthRepository,
    @GuestDisplayName val guestDisplayName: String,
) {

    /** Hot stream of the signed-in user, or `null` when signed out. */
    val user: Flow<UserProfile?> = authRepository.observeCurrentUser()
}
