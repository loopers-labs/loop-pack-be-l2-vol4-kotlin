package com.loopers.metrics.domain

interface ProductMetricsRepository {
    fun upsertDelta(productId: Long, likeDelta: Long = 0, salesDelta: Long = 0, viewDelta: Long = 0)
}
