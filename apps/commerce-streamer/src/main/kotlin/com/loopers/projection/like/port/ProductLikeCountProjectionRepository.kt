package com.loopers.projection.like.port

import java.time.ZonedDateTime

interface ProductLikeCountProjectionRepository {
    fun increment(productId: Long): Int

    fun decrement(productId: Long): Int

    fun applyDelta(
        productId: Long,
        likeDelta: Int,
        salesDelta: Int,
        viewDelta: Int,
        occurredAt: ZonedDateTime,
    ): ProductMetricsUpdateStatus
}
