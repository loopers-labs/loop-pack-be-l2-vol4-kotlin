package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class RedisRankingRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>
) : RankingRepository {
    override fun addScore(date: LocalDate, productId: Long, delta: Double) {
        val key = "ranking:" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val zincrby = redisTemplate.opsForZSet().incrementScore(key, productId.toString(), delta)
        redisTemplate.expire(key, Duration.ofDays(3))
    }

    override fun setScores(date: LocalDate, scores: Map<Long, Double>) {
        if (scores.isEmpty()) return
        val key = "ranking:" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val tuples = scores.map { (productId, score) ->
            ZSetOperations.TypedTuple.of(productId.toString(), score)
        }.toSet()
        redisTemplate.opsForZSet().add(key, tuples)
        redisTemplate.expire(key, Duration.ofDays(3))
    }
}
