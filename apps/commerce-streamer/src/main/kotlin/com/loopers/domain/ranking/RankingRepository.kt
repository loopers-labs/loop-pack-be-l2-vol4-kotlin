package com.loopers.domain.ranking

import java.time.LocalDate

interface RankingRepository {
    fun addScore(date: LocalDate, productId: Long, delta: Double)

    fun setScores(date: LocalDate, scores: Map<Long, Double>)
}
