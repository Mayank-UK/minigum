package com.minigum.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.minigum.data.remote.awaitUnit
import com.minigum.data.remote.awaitValue
import com.minigum.data.remote.dtos.UserDto
import com.minigum.data.remote.dtos.toUserDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Thin remote data source over [FirebaseAuth]. Analogous to [ApiService]: returns wire [UserDto]s
 * and throws on failure — translation to [com.minigum.core.result.Result] happens in the repository.
 */
interface AuthService {
    fun observeCurrentUser(fallbackDisplayName: String): Flow<UserDto?>
    suspend fun currentUser(fallbackDisplayName: String): UserDto?
    suspend fun signUp(email: String, password: String, displayName: String): UserDto
    suspend fun signIn(email: String, password: String): UserDto
    suspend fun signOut()
}

class AuthServiceImpl(
    private val auth: FirebaseAuth,
) : AuthService {

    override fun observeCurrentUser(fallbackDisplayName: String): Flow<UserDto?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a -> trySend(a.currentUser.toUserDto(fallbackDisplayName)) }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser.toUserDto(fallbackDisplayName))
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun currentUser(fallbackDisplayName: String): UserDto? =
        auth.currentUser.toUserDto(fallbackDisplayName)

    override suspend fun signUp(email: String, password: String, displayName: String): UserDto {
        // Email/password accounts don't carry a display name by default, so set it on the profile
        // immediately after creation — the leaderboard uses it to label rows.
        val result = auth.createUserWithEmailAndPassword(email, password).awaitValue()
        val user = result.user ?: error("FirebaseAuth.createUser succeeded but returned no user")
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build()
        ).awaitUnit()
        return user.toUserDto(fallbackDisplayName = displayName.trim())!!
    }

    override suspend fun signIn(email: String, password: String): UserDto {
        val result = auth.signInWithEmailAndPassword(email, password).awaitValue()
        val user = result.user ?: error("FirebaseAuth.signIn succeeded but returned no user")
        return user.toUserDto(fallbackDisplayName = user.displayName.orEmpty())!!
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}
