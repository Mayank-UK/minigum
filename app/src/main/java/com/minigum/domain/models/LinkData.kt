package com.minigum.domain.models

import androidx.annotation.DrawableRes
import java.util.UUID

data class LinkData(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    @DrawableRes val image: Int,
    val routePath: String
)