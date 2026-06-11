package com.loopers.domain.product

interface LikeCountQueryPort {
    fun countByProductId(productId: Long): Long
    fun countsByProductIds(productIds: List<Long>): Map<Long, Long>
}
