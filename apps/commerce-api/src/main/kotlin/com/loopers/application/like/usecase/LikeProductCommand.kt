package com.loopers.application.like.usecase

data class LikeProductCommand(
    val loginId: String,
    val password: String,
    val productId: Long,
)
