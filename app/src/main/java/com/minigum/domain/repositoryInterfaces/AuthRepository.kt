package com.minigum.domain.repositoryinterfaces

import com.minigum.core.result.Result
import com.minigum.domain.models.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Authentication and the live identity of the signed-in user.
 *
 * Mirrors [PostRepository] in shape: every suspend operation returns a [Result], and the hot
 * [observeCurrentUser] stream lets the UI (drawer, leaderboard gating) react to sign-in / sign-out
 * without polling.
 */
interface AuthRepository {

    /** Hot stream of the current user, or `null` when signed out. */
    fun observeCurrentUser(): Flow<UserProfile?>

    /** One-shot snapshot of the current user, or `null` when signed out. */
    suspend fun currentUser(): UserProfile?

    suspend fun signUp(email: String, password: String, displayName: String): Result<UserProfile>

    suspend fun signIn(email: String, password: String): Result<UserProfile>

    suspend fun signOut(): Result<Unit>
}
