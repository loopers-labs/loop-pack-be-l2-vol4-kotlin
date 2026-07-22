package com.loopers.projection.ranking.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "commerce-ranking")
data class RankingProperties(
    val score: RankingScoreProperties,
    val carryOver: RankingCarryOverProperties,
    val rdbSync: RankingRdbSyncProperties,
)
