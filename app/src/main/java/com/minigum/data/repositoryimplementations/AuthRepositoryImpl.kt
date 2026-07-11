package com.minigum.data.repositoryimplementations

import com.minigum.core.guards.guardedFirebaseCall
import com.minigum.core.guards.safeRepositoryCall
import com.minigum.core.result.Result
import com.minigum.data.mappers.toDomainProfile
import com.minigum.data.remote.AuthService
import com.minigum.domain.models.UserProfile
import com.minigum.domain.repositoryinterfaces.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implements [AuthRepository] on top of [AuthService], wrapping every Firebase call in
 * [guardedFirebaseCall] (so platform exceptions become [com.minigum.core.exceptions.AppException]s)
 * and the whole operation in [safeRepositoryCall] (so the domain/UI only ever see a [Result]).
 *
 * [fallbackDisplayName] keeps a stable guest name for the unauthenticated state, matching the
 * pre-Firebase [com.minigum.domain.session.UserSession] behaviour.
 *
 * `safeRepositoryCall` has several optional callback params, so the block is passed with its
 * `block =` name explicitly (a trailing lambda would otherwise bind to the last callback).
 */
class AuthRepositoryImpl @Inject constructor(
    private val authService: AuthService,
    private val fallbackDisplayName: String,
) : AuthRepository {

    override fun observeCurrentUser(): Flow<UserProfile?> =
        authService.observeCurrentUser(fallbackDisplayName).map { it?.toDomainProfile() }

    override suspend fun currentUser(): UserProfile? =
        guardedFirebaseCall { authService.currentUser(fallbackDisplayName)?.toDomainProfile() }

    override suspend fun signUp(email: String, password: String, displayName: String): Result<UserProfile> =
        safeRepositoryCall(block = {
            guardedFirebaseCall { authService.signUp(email, password, displayName).toDomainProfile() }
        })

    override suspend fun signIn(email: String, password: String): Result<UserProfile> =
        safeRepositoryCall(block = {
            guardedFirebaseCall { authService.signIn(email, password).toDomainProfile() }
        })

    override suspend fun signOut(): Result<Unit> =
        safeRepositoryCall(block = {
            guardedFirebaseCall { authService.signOut() }
        })
}
