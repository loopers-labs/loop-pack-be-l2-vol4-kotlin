package com.loopers.application.ranking

import com.loopers.config.redis.RankingDatePolicy
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.domain.ranking.RankingCarryOverRepository
import com.loopers.domain.ranking.RankingWeights
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

@Component
class RankingCarryOverService(
    private val repository: RankingCarryOverRepository,
    private val properties: RankingRedisProperties,
) {
    private val datePolicy = RankingDatePolicy(properties)

    fun carryOver(baseDate: LocalDate): Long {
        val sourceDate = baseDate.minusDays(1)
        val ownerId = UUID.randomUUID().toString()
        val lockTtl = Duration.ofSeconds(properties.carryOver.lockTtlSeconds)
        if (!repository.tryAcquireLock(sourceDate, ownerId, lockTtl)) {
            log.info("Ranking carry-over lock was not acquired. sourceDate={}, targetDate={}", sourceDate, baseDate)
            return 0L
        }

        return try {
            repository.carryOver(
                sourceDate = sourceDate,
                targetDate = baseDate,
                topN = properties.carryOver.topN,
                factor = properties.carryOver.factor,
                defaultWeights = RankingWeights(
                    view = properties.viewWeight,
                    like = properties.likeWeight,
                    sales = properties.salesWeight,
                ),
                expiresAt = datePolicy.expiresAt(baseDate),
            )
        } finally {
            runCatching {
                repository.releaseLock(sourceDate, ownerId)
            }.onFailure { e ->
                log.warn("Failed to release ranking carry-over lock. sourceDate={}, ownerId={}", sourceDate, ownerId, e)
            }
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RankingCarryOverService::class.java)
    }
}
