package com.minigum.di

import com.minigum.BuildConfig
import com.minigum.core.config.DevConfig
import com.minigum.data.local.AppDatabase
import com.minigum.data.local.daos.FailingPostDao
import com.minigum.data.local.daos.PostDao
import com.minigum.data.remote.ApiService
import com.minigum.data.remote.FailureInjectionInterceptor
import com.minigum.data.remote.FakeApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FlavorNetworkModule {

    /**
     * The dev flavor uses the **real** Retrofit client hitting [BuildConfig.BASE_URL] (same as
     * prod/staging), with a [FailureInjectionInterceptor] layered in front of it to simulate API
     * failures on the client side.
     *
     * The scenario comes from [DevConfig] — the single source of truth — and is shared with the
     * [FakeApiService] path so both simulation surfaces agree. The interceptor is a singleton so
     * it can also be toggled at runtime from a debug menu.
     */
    @Provides
    @Singleton
    fun provideFailureInjectionInterceptor(): FailureInjectionInterceptor =
        FailureInjectionInterceptor(DevConfig.apiScenario)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        failureInterceptor: FailureInjectionInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(failureInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Swaps between the real Retrofit [ApiService] and [FakeApiService] based on [DevConfig].
     * Retrofit is still built either way (the interceptor chain is harmless when unused).
     */
    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        if (DevConfig.useFakeApi) {
            FakeApiService(DevConfig.apiScenario)
        } else {
            retrofit.create(ApiService::class.java)
        }

    /**
     * Swaps between the real Room [PostDao] and the [FailingPostDao] decorator based on
     * [DevConfig]. (Provided per-flavor — staging/prod return the real DAO — so the dev-only
     * fake can override without a Hilt duplicate-binding conflict.)
     */
    @Provides
    @Singleton
    fun providePostDao(database: AppDatabase): PostDao =
        if (DevConfig.useFakeDatabase) {
            FailingPostDao(database.postDao(), DevConfig.dbScenario)
        } else {
            database.postDao()
        }
}
