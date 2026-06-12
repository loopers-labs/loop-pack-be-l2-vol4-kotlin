package com.loopers.domain.like.port

interface ProductLikeCountRepository {
    fun create(productId: Long)
    fun increment(productId: Long)
    fun decrement(productId: Long)
    fun countByProductId(productId: Long): Long
    fun countByProductIds(productIds: Set<Long>): Map<Long, Long>
}
