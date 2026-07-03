package com.loopers.domain.like

data class ProductLikedEvent(
    val userId: Long,
    val productId: Long,
)

data class ProductUnlikedEvent(
    val userId: Long,
    val productId: Long,
)
