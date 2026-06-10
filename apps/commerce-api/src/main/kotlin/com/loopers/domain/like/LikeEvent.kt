package com.loopers.domain.like

data class LikeCreatedEvent(
    val productId: Long,
)

data class LikeDeletedEvent(
    val productId: Long,
)
