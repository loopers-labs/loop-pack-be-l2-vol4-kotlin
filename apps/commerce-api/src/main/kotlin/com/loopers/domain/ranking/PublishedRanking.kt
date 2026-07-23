package com.loopers.domain.ranking

import java.time.LocalDate

data class PublishedRanking(
    val period: RankingPeriod,
    val baseDate: LocalDate,
    val generationId: String,
)
