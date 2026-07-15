package com.loopers.domain.ranking

import com.loopers.domain.metrics.ProductMetricsModel

object RankingScoreCalculator {
    fun dailyScores(
        metrics: List<ProductMetricsModel>,
        baselines: Map<Long, ProductRankingBaseline>,
    ): Map<Long, Double> =
        metrics.mapNotNull { m ->
            val base = baselines[m.id]
            val view = m.viewCount - (base?.viewCount ?: 0)
            val like = m.likeCount - (base?.likeCount ?: 0)
            val sales = m.salesCount - (base?.salesCount ?: 0)
            val score = view * RankingScorePolicy.VIEW_WEIGHT +
                like * RankingScorePolicy.LIKE_WEIGHT +
                sales * RankingScorePolicy.ORDER_WEIGHT
            if (score > 0) m.id to score else null
        }.toMap()
}