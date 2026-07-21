package com.loopers.domain.productmetric

import java.time.LocalDate

class ProductMetricDaily(
    val id: Long = 0L,
    val metricDate: LocalDate,
    val productId: Long,
    val viewCount: Long,
    val likeCount: Long,
    val salesAmount: Long,
)
