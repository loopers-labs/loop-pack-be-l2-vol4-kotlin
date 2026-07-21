package com.loopers.domain.productmetric

import java.time.LocalDate

class ProductMetricWeekly(
    val id: Long = 0L,
    val baseDate: LocalDate,
    val productId: Long,
    val viewCount: Long,
    val likeCount: Long,
    val salesAmount: Long,
)
