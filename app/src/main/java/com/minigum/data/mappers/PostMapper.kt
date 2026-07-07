package com.minigum.data.mappers

import com.minigum.data.local.entities.PostEntity
import com.minigum.data.remote.dtos.PostDto
import com.minigum.domain.models.Post

object PostMapper {
    fun PostDto.toDomain() = Post(
        id = id,
        userId = userId,
        title = title,
        body = body
    )

    fun PostEntity.toDomain() = Post(
        id = id,
        userId = userId,
        title = title,
        body = body
    )

    fun Post.toEntity() = PostEntity(
        id = id,
        userId = userId,
        title = title,
        body = body
    )

    fun Post.toDto() = PostDto(
        id = id,
        userId = userId,
        title = title,
        body = body
    )
}