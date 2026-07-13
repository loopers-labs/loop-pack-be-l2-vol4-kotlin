package com.loopers.application.ranking

import com.loopers.config.redis.RankingClockConfig
import com.loopers.config.redis.RankingDatePolicy
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.domain.ranking.RankingCarryOverRepository
import com.loopers.domain.ranking.RankingWeights
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

@Component
class RankingCarryOverService(
    private val repository: RankingCarryOverRepository,
    private val properties: RankingRedisProperties,
    @Qualifier(RankingClockConfig.RANKING_CLOCK)
    private val clock: Clock,
) {
    private val datePolicy = RankingDatePolicy(properties)

    fun carryToday(): Long {
        return carryOver(LocalDate.now(clock.withZone(properties.zoneId)))
    }

    fun carryOver(sourceDate: LocalDate): Long {
        val ownerId = UUID.randomUUID().toString()
        val lockTtl = Duration.ofSeconds(properties.carryOver.lockTtlSeconds)
        if (!repository.tryAcquireLock(sourceDate, ownerId, lockTtl)) {
            log.info("Ranking carry-over lock was not acquired. date={}", sourceDate)
            return 0L
        }

        return try {
            val targetDate = sourceDate.plusDays(1)
            repository.carryOver(
                sourceDate = sourceDate,
                targetDate = targetDate,
                topN = properties.carryOver.topN,
                factor = properties.carryOver.factor,
                defaultWeights = RankingWeights(
                    view = properties.viewWeight,
                    like = properties.likeWeight,
                    sales = properties.salesWeight,
                ),
                expiresAt = datePolicy.expiresAt(targetDate),
            )
        } finally {
            runCatching {
                repository.releaseLock(sourceDate, ownerId)
            }.onFailure { e ->
                log.warn("Failed to release ranking carry-over lock. date={}, ownerId={}", sourceDate, ownerId, e)
            }
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RankingCarryOverService::class.java)
    }
}
