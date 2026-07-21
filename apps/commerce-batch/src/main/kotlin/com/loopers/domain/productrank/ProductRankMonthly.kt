package com.loopers.domain.productrank

import java.time.LocalDate

class ProductRankMonthly(
    val id: Long = 0L,
    val baseDate: LocalDate,
    val productId: Long,
    val rankingScore: Double,
)
