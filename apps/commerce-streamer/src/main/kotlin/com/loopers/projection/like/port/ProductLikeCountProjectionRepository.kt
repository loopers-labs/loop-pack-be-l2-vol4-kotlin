package com.loopers.projection.like.port

import java.time.ZonedDateTime

interface ProductLikeCountProjectionRepository {
    fun applyDelta(
        productId: Long,
        likeDelta: Int,
        salesDelta: Long,
        viewDelta: Int,
        occurredAt: ZonedDateTime,
    ): ProductMetricsUpdateStatus
}
