package com.minigum.di

import android.content.Context
import androidx.room.Room
import com.minigum.data.local.AppDatabase
import com.minigum.util.APP_DATABASE_NAME
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Shared database wiring for all flavors (dev / staging / prod). Each flavor uses the same real
 * Room database, so this lives in `main` rather than being duplicated per flavor.
 *
 * Only the [AppDatabase] is provided here. The [com.minigum.data.local.daos.PostDao]
 * binding lives in each flavor's `FlavorNetworkModule`: dev can swap in
 * [com.minigum.data.local.daos.FailingPostDao] via `DevConfig`, while staging/prod
 * return the real DAO. DB error-handling paths are additionally verified via MockK unit tests.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            APP_DATABASE_NAME
        ).build()
    }
}
