package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.OrderQueueRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

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

    companion object {
        const val WAITING_KEY = "commerce-api:queue:order:waiting:v1"
    }
}
