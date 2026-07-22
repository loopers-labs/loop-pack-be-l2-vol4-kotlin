package com.loopers.domain.ranking

import java.time.LocalDate

interface ProductRankMvRepository {
    fun findTop100(
        period: RankingPeriod,
        baseDate: LocalDate,
    ): List<RankingEntry>
}
