package com.minigum.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.minigum.data.remote.AuthService
import com.minigum.data.remote.AuthServiceImpl
import com.minigum.data.remote.LeaderboardService
import com.minigum.data.remote.LeaderboardServiceImpl
import com.minigum.domain.session.GuestDisplayName
import com.minigum.domain.session.randomGuestName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the Firebase singletons and their thin service wrappers.
 *
 * Shared across all flavors (the Firebase project holds one combined `google-services.json`). The
 * repository bindings that depend on these services live in [RepositoryModule]; the existing
 * `FlavorNetworkModule` continues to own the Retrofit/Room flavor split untouched.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideAuthService(auth: FirebaseAuth): AuthService = AuthServiceImpl(auth)

    @Provides
    @Singleton
    fun provideLeaderboardService(db: FirebaseFirestore): LeaderboardService = LeaderboardServiceImpl(db)

    /**
     * One random guest name per app process; injected wherever a signed-out user needs a label,
     * so the drawer and any score rows created before a profile name is set agree on it.
     */
    @Provides
    @Singleton
    @GuestDisplayName
    fun provideGuestDisplayName(): String = randomGuestName()
}
