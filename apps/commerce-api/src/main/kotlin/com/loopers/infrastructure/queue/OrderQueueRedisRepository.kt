package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.OrderQueueRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OrderQueueRedisRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : OrderQueueRepository {
    private val zset get() = redisTemplate.opsForZSet()

    override fun enter(userId: Long, nowMillis: Long): Long {
        // ZADD NX: 이미 있으면 score(순번) 보존
        zset.addIfAbsent(WAITING_KEY, userId.toString(), nowMillis.toDouble())
        return (rank(userId) ?: 0L) + 1L
    }

    override fun rank(userId: Long): Long? = zset.rank(WAITING_KEY, userId.toString())

    override fun total(): Long = zset.size(WAITING_KEY) ?: 0L

    override fun pruneExpiredProcessing(beforeMillis: Long) {
        zset.removeRangeByScore(PROCESSING_KEY, Double.NEGATIVE_INFINITY, beforeMillis.toDouble())
    }

    override fun countActive(): Long = zset.size(PROCESSING_KEY) ?: 0L

    override fun popNext(count: Int): List<Long> {
        if (count <= 0) return emptyList()
        return zset.popMin(WAITING_KEY, count.toLong())
            ?.mapNotNull { it.value?.toLong() }
            ?: emptyList()
    }

    override fun issueToken(userId: Long, token: String, ttlSeconds: Long, nowMillis: Long) {
        redisTemplate.opsForValue().set(tokenKey(userId), token, Duration.ofSeconds(ttlSeconds))
        zset.add(PROCESSING_KEY, userId.toString(), nowMillis.toDouble())
    }

    override fun findToken(userId: Long): String? = redisTemplate.opsForValue().get(tokenKey(userId))

    override fun consume(userId: Long) {
        redisTemplate.delete(tokenKey(userId))
        zset.remove(PROCESSING_KEY, userId.toString())
    }

    companion object {
        const val WAITING_KEY = "commerce-api:queue:order:waiting:v1"
        const val PROCESSING_KEY = "commerce-api:queue:order:processing:v1"
        fun tokenKey(userId: Long) = "commerce-api:queue:order:token:v1:$userId"
    }
}
