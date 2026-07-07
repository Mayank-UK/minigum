package com.minigum.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minigum.data.local.entities.PostEntity

@Dao
interface PostDao {
    @Query("SELECT * FROM posts")
    suspend fun getPosts(): List<PostEntity>

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun getPostById(id: Int): PostEntity?

    @Query("SELECT * FROM posts WHERE userId = :userId")
    suspend fun getPostsByUserId(userId: Int): List<PostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<PostEntity>)
}