package com.loopers.domain.product

interface LikeCountQueryPort {
    fun countByProductId(productId: Long): Long
}
