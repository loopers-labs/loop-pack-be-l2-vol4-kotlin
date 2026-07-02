package com.loopers.projection.like.port

interface ProductLikeCountProjectionRepository {
    fun increment(productId: Long): Int

    fun decrement(productId: Long): Int
}
