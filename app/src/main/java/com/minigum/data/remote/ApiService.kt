package com.minigum.data.remote

import com.minigum.data.remote.dtos.PostDto
import retrofit2.http.GET
import retrofit2.http.Path

interface ApiService {
    @GET("posts")
    suspend fun getPosts(): List<PostDto>

    @GET("posts/{id}")
    suspend fun getPostById(@Path("id") id: Int): PostDto
}