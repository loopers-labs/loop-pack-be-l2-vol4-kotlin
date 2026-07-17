package com.loopers.metrics.domain

interface ProductMetricsRepository {
    fun accumulate(productId: Long, likeChange: Long = 0, salesChange: Long = 0, viewChange: Long = 0)
}
