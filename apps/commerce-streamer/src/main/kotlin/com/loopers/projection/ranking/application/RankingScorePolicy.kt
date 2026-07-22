package com.loopers.projection.ranking.application

import org.springframework.stereotype.Component

@Component
class RankingScorePolicy(
    private val properties: RankingProperties,
) {
    fun likeScore(delta: Int): Double = delta * properties.score.like

    fun orderScore(): Double = properties.score.order
}
