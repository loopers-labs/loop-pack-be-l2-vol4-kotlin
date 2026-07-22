package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingCarryOverRepository
import com.loopers.domain.ranking.RankingCarryOverUnavailableException
import com.loopers.domain.ranking.RankingWeights
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Component
class RedisRankingCarryOverRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingCarryOverRepository {
    private val carryOverScript = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource("redis/ranking-carry-over.lua"))
        resultType = Long::class.java
    }
    private val releaseLockScript = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource("redis/ranking-release-lock.lua"))
        resultType = Long::class.java
    }

    override fun tryAcquireLock(
        date: LocalDate,
        ownerId: String,
        ttl: Duration,
    ): Boolean = withUnavailableMapping {
        redisTemplate.opsForValue()
            .setIfAbsent(RankingRedisKeys.carryOverLock(date), ownerId, ttl) == true
    }

    override fun carryOver(
        sourceDate: LocalDate,
        targetDate: LocalDate,
        topN: Long,
        factor: Double,
        defaultWeights: RankingWeights,
        expiresAt: Instant,
    ): Long = withUnavailableMapping {
        redisTemplate.execute(
            carryOverScript,
            listOf(
                RankingRedisKeys.all(sourceDate),
                RankingRedisKeys.carry(targetDate),
                RankingRedisKeys.view(targetDate),
                RankingRedisKeys.like(targetDate),
                RankingRedisKeys.sales(targetDate),
                RankingRedisKeys.all(targetDate),
                RankingRedisKeys.ACTIVE_WEIGHTS,
            ),
            topN.toString(),
            factor.toString(),
            defaultWeights.view.toString(),
            defaultWeights.like.toString(),
            defaultWeights.sales.toString(),
            expiresAt.epochSecond.toString(),
        ) ?: 0L
    }

    override fun releaseLock(date: LocalDate, ownerId: String) {
        withUnavailableMapping {
            redisTemplate.execute(
                releaseLockScript,
                listOf(RankingRedisKeys.carryOverLock(date)),
                ownerId,
            )
        }
    }

    private fun <T> withUnavailableMapping(block: () -> T): T {
        return try {
            block()
        } catch (e: DataAccessException) {
            throw RankingCarryOverUnavailableException(e)
        }
    }
}
