package com.loopers.ranking.infrastructure

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class RankingZSetReader(
    private val redisTemplate: RedisTemplate<String, String>,
) {
    @CircuitBreaker(name = CIRCUIT)
    fun reverseRange(key: String, offset: Long, endInclusive: Long): List<RankingScore> =
        redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, offset, endInclusive)
            .orEmpty()
            .mapNotNull { tuple ->
                val member = tuple.value ?: return@mapNotNull null
                val score = tuple.score ?: return@mapNotNull null
                RankingScore(member.toLong(), score)
            }

    @CircuitBreaker(name = CIRCUIT)
    fun score(key: String, productId: Long): Double? =
        redisTemplate.opsForZSet().score(key, productId.toString())

    @CircuitBreaker(name = CIRCUIT)
    fun countHigherThan(key: String, score: Double): Long =
        redisTemplate.execute { connection ->
            connection.zSetCommands().zCount(
                key.toByteArray(Charsets.UTF_8),
                Range.of(Range.Bound.exclusive(score), Range.Bound.unbounded()),
            )
        } ?: 0L

    companion object {
        const val CIRCUIT = "ranking-read"
    }
}

data class RankingScore(
    val productId: Long,
    val score: Double,
)
