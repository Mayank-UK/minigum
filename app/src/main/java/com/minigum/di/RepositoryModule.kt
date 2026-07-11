package com.minigum.di

import com.minigum.data.local.daos.PostDao
import com.minigum.data.remote.ApiService
import com.minigum.data.remote.AuthService
import com.minigum.data.remote.LeaderboardService
import com.minigum.data.repositoryimplementations.AuthRepositoryImpl
import com.minigum.data.repositoryimplementations.LeaderboardRepositoryImpl
import com.minigum.data.repositoryimplementations.PostRepositoryImpl
import com.minigum.domain.repositoryinterfaces.AuthRepository
import com.minigum.domain.repositoryinterfaces.LeaderboardRepository
import com.minigum.domain.repositoryinterfaces.PostRepository
import com.minigum.domain.session.GuestDisplayName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providePostRepository(
        api: ApiService,
        dao: PostDao
    ): PostRepository {
        return PostRepositoryImpl(api, dao)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        authService: AuthService,
        @GuestDisplayName guestDisplayName: String,
    ): AuthRepository = AuthRepositoryImpl(authService, guestDisplayName)

    @Provides
    @Singleton
    fun provideLeaderboardRepository(
        service: LeaderboardService,
    ): LeaderboardRepository = LeaderboardRepositoryImpl(service)
}