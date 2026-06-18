package com.loopers.domain.like.port

import com.loopers.domain.like.model.LikeModel

interface LikeRepository {
    fun save(like: LikeModel): Int

    fun delete(
        userId: Long,
        productId: Long,
    ): Int
}
