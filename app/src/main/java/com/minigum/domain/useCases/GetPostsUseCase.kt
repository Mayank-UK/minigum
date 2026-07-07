package com.minigum.domain.usecases

import com.minigum.core.result.Result
import com.minigum.domain.models.Post
import com.minigum.domain.repositoryinterfaces.PostRepository
import javax.inject.Inject

class GetPostsUseCase @Inject constructor(private val repository: PostRepository) {
    suspend operator fun invoke(): Result<List<Post>> = repository.getPosts()
}
