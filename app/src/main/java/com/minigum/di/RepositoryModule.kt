package com.minigum.di

import com.minigum.data.local.daos.PostDao
import com.minigum.data.remote.ApiService
import com.minigum.data.repositoryimplementations.PostRepositoryImpl
import com.minigum.domain.repositoryinterfaces.PostRepository
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
}