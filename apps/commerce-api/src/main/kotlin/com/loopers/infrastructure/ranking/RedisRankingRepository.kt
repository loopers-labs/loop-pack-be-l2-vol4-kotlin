package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingPage
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingUnavailableException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RedisRankingRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingRepository {
    override fun findPage(
        date: LocalDate,
        page: Int,
        size: Int,
    ): RankingPage = withUnavailableMapping {
        val key = RankingRedisKeys.all(date)
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

        RankingPage(
            entries = entries,
            totalElements = redisTemplate.opsForZSet().zCard(key) ?: 0L,
        )
    }

    override fun findRank(date: LocalDate, productId: Long): Long? = withUnavailableMapping {
        redisTemplate.opsForZSet()
            .reverseRank(RankingRedisKeys.all(date), productId.toString())
            ?.plus(1)
    }

    private fun <T> withUnavailableMapping(block: () -> T): T {
        return try {
            block()
        } catch (e: DataAccessException) {
            throw RankingUnavailableException(e)
        }
    }
}
