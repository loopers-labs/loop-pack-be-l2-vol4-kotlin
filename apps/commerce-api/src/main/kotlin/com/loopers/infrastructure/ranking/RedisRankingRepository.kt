package com.loopers.infrastructure.ranking

import com.loopers.application.ranking.RankingBaseDatePolicy
import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.ProductRankMvRepository
import com.loopers.domain.ranking.ProductRankPublicationRepository
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingPage
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingUnavailableException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

@Component
class RedisRankingRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val productRankPublicationRepository: ProductRankPublicationRepository,
    private val productRankMvRepository: ProductRankMvRepository,
    private val baseDatePolicy: RankingBaseDatePolicy,
    private val properties: RankingRedisProperties,
) : RankingRepository {
    private val releaseLockScript = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource("redis/ranking-release-lock.lua"))
        resultType = Long::class.java
    }

    override fun findPage(
        period: RankingPeriod,
        date: LocalDate,
        page: Int,
        size: Int,
    ): RankingPage = withUnavailableMapping {
        when (period) {
            RankingPeriod.DAILY -> findRedisPage(RankingRedisKeys.all(date), page, size)
            RankingPeriod.WEEKLY, RankingPeriod.MONTHLY -> findPeriodPage(period, date, page, size)
        }
    }

    override fun findRank(date: LocalDate, productId: Long): Long? = withUnavailableMapping {
        redisTemplate.opsForZSet()
            .reverseRank(RankingRedisKeys.all(date), productId.toString())
            ?.plus(1)
    }

    private fun findPeriodPage(
        period: RankingPeriod,
        date: LocalDate,
        page: Int,
        size: Int,
    ): RankingPage {
        val requestedBaseDate = baseDatePolicy.normalize(period, date)
        val published = productRankPublicationRepository.findLatestPublished(period, requestedBaseDate)
            ?: return RankingPage(entries = emptyList(), totalElements = 0)
        val key = periodCacheKey(period, published.baseDate, published.generationId)
        if (redisTemplate.hasKey(key) != true) {
            fillPeriodCache(period, published.baseDate, published.generationId, key)
        }
        return findRedisPage(key, page, size)
    }

    private fun findRedisPage(
        key: String,
        page: Int,
        size: Int,
    ): RankingPage {
        val start = page.toLong() * size
        val end = start + size - 1
        val entries = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, start, end)
            .orEmpty()
            .mapIndexedNotNull { index, tuple ->
                val productId = tuple.value?.toLongOrNull() ?: return@mapIndexedNotNull null
                val score = tuple.score ?: return@mapIndexedNotNull null
                RankingEntry(
                    productId = productId,
                    rank = start + index + 1,
                    score = score,
                )
            }

        return RankingPage(
            entries = entries,
            totalElements = redisTemplate.opsForZSet().zCard(key) ?: 0L,
        )
    }

    private fun fillPeriodCache(
        period: RankingPeriod,
        baseDate: LocalDate,
        generationId: String,
        key: String,
    ) {
        val lockKey = periodFillLockKey(period, baseDate, generationId)
        val ownerId = UUID.randomUUID().toString()
        val lockTtl = Duration.ofSeconds(properties.periodCache.fillLockTtlSeconds)
        if (redisTemplate.opsForValue().setIfAbsent(lockKey, ownerId, lockTtl) != true) {
            return
        }

        try {
            if (redisTemplate.hasKey(key) == true) {
                return
            }
            loadPeriodCache(period, baseDate, key)
        } finally {
            releaseLock(lockKey, ownerId)
        }
    }

    private fun loadPeriodCache(
        period: RankingPeriod,
        baseDate: LocalDate,
        key: String,
    ) {
        val ranks = productRankMvRepository.findTop100(period, baseDate)
        if (ranks.isEmpty()) {
            return
        }
        ranks.forEach { rank ->
            redisTemplate.opsForZSet().add(key, rank.productId.toString(), rank.score)
        }
        redisTemplate.expire(key, periodCacheTtl(period))
    }

    private fun releaseLock(lockKey: String, ownerId: String) {
        redisTemplate.execute(
            releaseLockScript,
            listOf(lockKey),
            ownerId,
        )
    }

    private fun periodCacheKey(
        period: RankingPeriod,
        baseDate: LocalDate,
        generationId: String,
    ): String {
        return when (period) {
            RankingPeriod.WEEKLY -> RankingRedisKeys.weekly(baseDate, generationId)
            RankingPeriod.MONTHLY -> RankingRedisKeys.monthly(baseDate, generationId)
            RankingPeriod.DAILY -> error("Daily ranking does not use period cache key.")
        }
    }

    private fun periodFillLockKey(
        period: RankingPeriod,
        baseDate: LocalDate,
        generationId: String,
    ): String {
        return when (period) {
            RankingPeriod.WEEKLY -> RankingRedisKeys.weeklyFillLock(baseDate, generationId)
            RankingPeriod.MONTHLY -> RankingRedisKeys.monthlyFillLock(baseDate, generationId)
            RankingPeriod.DAILY -> error("Daily ranking does not use period cache fill lock.")
        }
    }

    private fun periodCacheTtl(period: RankingPeriod): Duration {
        return when (period) {
            RankingPeriod.WEEKLY -> Duration.ofDays(properties.periodCache.weeklyTtlDays)
            RankingPeriod.MONTHLY -> Duration.ofDays(properties.periodCache.monthlyTtlDays)
            RankingPeriod.DAILY -> error("Daily ranking does not use period cache TTL.")
        }
    }

    private fun <T> withUnavailableMapping(block: () -> T): T {
        return try {
            block()
        } catch (e: DataAccessException) {
            throw RankingUnavailableException(e)
        }
    }
}
