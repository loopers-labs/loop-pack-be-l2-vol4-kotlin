package com.loopers.domain.productmetric

import java.time.LocalDate

interface ProductMetricDailyRepository {
    fun increment(
        metricDate: LocalDate,
        productId: Long,
        viewCountDelta: Long,
        likeCountDelta: Long,
        salesAmountDelta: Long,
    )

    fun find(
        metricDate: LocalDate,
        productId: Long,
    ): ProductMetricDaily?
}
