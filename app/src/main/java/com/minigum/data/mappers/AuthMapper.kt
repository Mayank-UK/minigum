package com.minigum.data.mappers

import com.minigum.data.remote.dtos.UserDto
import com.minigum.data.remote.dtos.toDomain

/** Maps a [UserDto] to its domain [com.minigum.domain.models.UserProfile]. */
fun UserDto.toDomainProfile() = toDomain()
