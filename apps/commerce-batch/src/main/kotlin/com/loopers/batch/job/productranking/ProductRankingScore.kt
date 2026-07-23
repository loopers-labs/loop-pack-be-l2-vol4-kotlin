package com.loopers.batch.job.productranking

import java.time.LocalDate

data class ProductRankingScore(
    val baseDate: LocalDate,
    val productId: Long,
    val rankingScore: Double,
)
