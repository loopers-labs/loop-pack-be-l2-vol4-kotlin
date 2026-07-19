package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingPolicyRepository
import com.loopers.domain.ranking.RankingUnavailableException
import com.loopers.domain.ranking.RankingWeights
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

@Component
class RedisRankingPolicyRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingPolicyRepository {
    private val updateWeightsScript = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource("redis/ranking-update-weights.lua"))
        resultType = Long::class.java
    }

    override fun updateWeights(
        date: LocalDate,
        weights: RankingWeights,
        expiresAt: Instant,
    ) {
        try {
            redisTemplate.execute(
                updateWeightsScript,
                listOf(
                    RankingRedisKeys.ACTIVE_WEIGHTS,
                    RankingRedisKeys.carry(date),
                    RankingRedisKeys.view(date),
                    RankingRedisKeys.like(date),
                    RankingRedisKeys.sales(date),
                    RankingRedisKeys.all(date),
                ),
                weights.view.toString(),
                weights.like.toString(),
                weights.sales.toString(),
                expiresAt.epochSecond.toString(),
            )
        } catch (e: DataAccessException) {
            throw RankingUnavailableException(e)
        }
    }
}
