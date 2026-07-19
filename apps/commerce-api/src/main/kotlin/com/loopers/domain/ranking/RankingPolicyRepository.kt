package com.loopers.domain.ranking

import java.time.Instant
import java.time.LocalDate

interface RankingPolicyRepository {
    fun updateWeights(
        date: LocalDate,
        weights: RankingWeights,
        expiresAt: Instant,
    )
}

data class RankingWeights(
    val view: Double,
    val like: Double,
    val sales: Double,
) {
    init {
        require(view.isFinite() && view >= 0.0)
        require(like.isFinite() && like >= 0.0)
        require(sales.isFinite() && sales >= 0.0)
    }
}
