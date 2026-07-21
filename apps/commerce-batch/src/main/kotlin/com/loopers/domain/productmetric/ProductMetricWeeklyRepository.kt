package com.loopers.domain.productmetric

import java.time.LocalDate

interface ProductMetricWeeklyRepository {
    fun upsert(
        baseDate: LocalDate,
        productId: Long,
        viewCount: Long,
        likeCount: Long,
        salesAmount: Long,
    )

    fun deleteByBaseDate(baseDate: LocalDate)

    fun find(
        baseDate: LocalDate,
        productId: Long,
    ): ProductMetricWeekly?
}
