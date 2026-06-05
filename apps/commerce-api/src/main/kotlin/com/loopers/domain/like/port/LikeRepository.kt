package com.loopers.domain.like.port

import com.loopers.domain.like.model.LikeModel

interface LikeRepository {
    fun exists(
        userId: Long,
        productId: Long,
    ): Boolean

    fun save(like: LikeModel): LikeModel

    fun delete(
        userId: Long,
        productId: Long,
    )

    fun countByProductId(productId: Long): Long
    fun countByProductIds(productIds: Set<Long>): Map<Long, Long>
}
