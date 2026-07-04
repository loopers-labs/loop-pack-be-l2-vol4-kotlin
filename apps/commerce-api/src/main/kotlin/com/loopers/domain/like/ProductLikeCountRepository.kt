package com.loopers.domain.like

import java.time.ZonedDateTime

interface ProductLikeCountRepository {
    fun findByProductIdIn(productIds: List<Long>): List<ProductLikeCountModel>

    fun upsertLikeCount(productId: Long, delta: Int, now: ZonedDateTime): Int
}
