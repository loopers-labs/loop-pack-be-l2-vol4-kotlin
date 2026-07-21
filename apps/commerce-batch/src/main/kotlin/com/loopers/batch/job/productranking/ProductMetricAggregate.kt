package com.loopers.batch.job.productranking

import java.time.LocalDate

data class ProductMetricAggregate(
    val baseDate: LocalDate,
    val productId: Long,
    val viewCount: Long,
    val likeCount: Long,
    val salesAmount: Long,
)
