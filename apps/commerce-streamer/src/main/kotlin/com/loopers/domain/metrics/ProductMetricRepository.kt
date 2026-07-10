package com.loopers.domain.metrics

interface ProductMetricRepository {
    fun upsertDelta(productId: Long, likeDelta: Long, salesDelta: Long, viewDelta: Long)
    fun findByProductId(productId: Long): ProductMetric?
}
