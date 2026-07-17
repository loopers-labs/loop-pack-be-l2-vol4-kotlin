package com.loopers.domain.event

data class ProductLikedEvent(
    val userId: Long,
    val productId: Long,
)

data class ProductUnlikedEvent(
    val userId: Long,
    val productId: Long,
)

data class ProductViewedEvent(
    val userId: Long?,
    val productId: Long,
)
