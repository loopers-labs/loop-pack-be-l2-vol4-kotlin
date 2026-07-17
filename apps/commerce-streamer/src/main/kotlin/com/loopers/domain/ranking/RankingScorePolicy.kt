package com.loopers.domain.ranking

import kotlin.math.ln

object RankingScorePolicy {
    const val VIEW_SCORE = 0.1
    const val LIKE_SCORE = 0.2
    private const val ORDER_WEIGHT = 0.6

    fun orderItemScore(amount: Long): Double = ORDER_WEIGHT * ln(1.0 + amount)
}
