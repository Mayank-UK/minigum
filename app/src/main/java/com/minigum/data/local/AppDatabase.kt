package com.minigum.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.minigum.data.local.daos.PostDao
import com.minigum.data.local.entities.PostEntity

@Database(entities = [
    PostEntity::class,
    // Add other entities here
                     ], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
}