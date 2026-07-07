package com.minigum.domain.usecases

import com.minigum.core.result.Result
import com.minigum.domain.models.Post
import com.minigum.domain.repositoryinterfaces.PostRepository
import javax.inject.Inject

class GetPostDetailUseCase @Inject constructor(private val repository: PostRepository) {
    suspend operator fun invoke(id: Int): Result<Post> = repository.getPostById(id)
}
