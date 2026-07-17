package com.loopers.domain.like.port

interface ProductLikeCountRepository {
    fun create(productId: Long)
    fun countByProductId(productId: Long): Long
    fun countByProductIds(productIds: Set<Long>): Map<Long, Long>
    fun rebuildFromLikes()
    fun countProductRows(): Long
    fun countLikeRows(): Long
    fun countProjectionRows(): Long
}
