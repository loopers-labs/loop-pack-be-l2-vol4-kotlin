package com.loopers.domain.ranking

import java.time.LocalDate

interface RankingRepository {
    fun addScore(date: LocalDate, productId: Long, delta: Double)
}
