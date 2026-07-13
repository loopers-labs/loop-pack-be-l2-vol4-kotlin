package com.loopers.application.ranking

import com.loopers.application.ranking.dto.RankingWeightCommand
import com.loopers.config.redis.RankingClockConfig
import com.loopers.config.redis.RankingDatePolicy
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.domain.ranking.RankingPolicyRepository
import com.loopers.domain.ranking.RankingWeights
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class RankingPolicyService(
    private val rankingPolicyRepository: RankingPolicyRepository,
    private val properties: RankingRedisProperties,
    @Qualifier(RankingClockConfig.RANKING_CLOCK)
    private val clock: Clock,
) {
    private val datePolicy = RankingDatePolicy(properties)

    fun updateTodayWeights(command: RankingWeightCommand): RankingWeights {
        val today = LocalDate.now(clock.withZone(properties.zoneId))
        val weights = command.toWeights()
        rankingPolicyRepository.updateWeights(
            date = today,
            weights = weights,
            expiresAt = datePolicy.expiresAt(today),
        )
        return weights
    }
}
