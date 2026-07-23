package com.loopers.batch.job.metrics

data class ProductDailyDelta(
    val productId: Long,
    val cumulativeLike: Int,
    val cumulativeSales: Int,
    val cumulativeView: Int,
    val deltaLike: Int,
    val deltaSales: Int,
    val deltaView: Int,
)