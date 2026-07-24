package com.loopers.infrastructure.ranking

import java.io.Serializable
import java.time.LocalDate

data class DailyProductRankingMetricsId(
    val productId: Long = 0,
    val metricDate: LocalDate = LocalDate.MIN,
) : Serializable
