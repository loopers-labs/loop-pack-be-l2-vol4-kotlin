package com.loopers.domain.productrank

import java.time.LocalDate

interface ProductRankMonthlyRepository {
    fun upsert(
        baseDate: LocalDate,
        productId: Long,
        rankingScore: Double,
    )

    fun deleteByBaseDate(baseDate: LocalDate)

    fun findTop100(baseDate: LocalDate): List<ProductRankMonthly>
}
