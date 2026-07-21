package com.loopers.batch.job.ranking

data class ProductMetricsAggregate(
    val productId: Long,
    val likeCount: Int,
    val salesCount: Int,
    val viewCount: Int,
)
