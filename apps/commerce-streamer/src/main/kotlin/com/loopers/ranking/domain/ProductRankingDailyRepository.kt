package com.loopers.ranking.domain

import java.math.BigDecimal
import java.time.LocalDate

interface ProductRankingDailyRepository {
    fun accumulate(rankingDate: LocalDate, productId: Long, change: BigDecimal)
}
