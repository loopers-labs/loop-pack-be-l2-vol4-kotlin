package com.loopers.infrastructure.ranking

import com.loopers.application.ranking.RankingKeyGenerator
import com.loopers.config.redis.RedisConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate

@Component
class RankingCacheUpdater(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun updateScores(scores: Map<DailyProductKey, Double>) {
        val failures = mutableListOf<DailyProductKey>()

        scores.forEach { (key, score) ->
            try {
                val redisKey = RankingKeyGenerator.daily(key.date)
                redisTemplate.opsForZSet().add(redisKey, key.productId.toString(), score)
                redisTemplate.expire(redisKey, RANKING_TTL)
            } catch (e: Exception) {
                log.error("Redis 랭킹 캐시 갱신 실패: productId={}, date={}", key.productId, key.date, e)
                failures.add(key)
            }
        }

        if (failures.isNotEmpty()) {
            throw RankingCacheUpdateException(failures)
        }
    }

    companion object {
        private val RANKING_TTL = Duration.ofDays(2)
    }
}

data class DailyProductKey(val productId: Long, val date: LocalDate)

class RankingCacheUpdateException(failures: List<DailyProductKey>) :
    RuntimeException("Redis 랭킹 캐시 갱신 실패: $failures")
