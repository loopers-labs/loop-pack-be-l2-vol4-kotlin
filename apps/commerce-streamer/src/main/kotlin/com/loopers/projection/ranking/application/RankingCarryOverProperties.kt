package com.loopers.projection.ranking.application

data class RankingCarryOverProperties(
    val enabled: Boolean,
    val cron: String,
    val decay: Double,
    val minScore: Double,
) {
    init {
        require(decay > 0.0 && decay < 1.0) { "decay 는 0 초과 1 미만이어야 합니다." }
        require(minScore >= 0.0) { "min-score 는 0 이상이어야 합니다." }
    }
}
