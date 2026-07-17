package com.loopers.projection.like.application

data class ProductMetricsDelta(
    val productId: Long,
    val likeDelta: Int = 0,
    val salesDelta: Long = 0,
    val viewDelta: Int = 0,
)
