package com.minigum.domain.repositoryinterfaces

import com.minigum.core.result.Result
import com.minigum.domain.models.Post

interface PostRepository {
    suspend fun getPosts(): Result<List<Post>>
    suspend fun getPostById(id: Int): Result<Post>
}
