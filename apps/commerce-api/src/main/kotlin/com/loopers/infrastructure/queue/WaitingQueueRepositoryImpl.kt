package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.WaitingQueueRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class WaitingQueueRepositoryImpl(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
) : WaitingQueueRepository {
    override fun enter(userId: Long, enteredAt: ZonedDateTime) {
        val score = enteredAt.toInstant().toEpochMilli().toDouble()
        masterRedisTemplate.opsForZSet().add(WAITING_KEY, userId.toString(), score)
    }

    override fun findRank(userId: Long): Long? {
        return masterRedisTemplate.opsForZSet().rank(WAITING_KEY, userId.toString())
    }

    override fun size(): Long {
        return masterRedisTemplate.opsForZSet().size(WAITING_KEY) ?: 0
    }

    override fun pop(count: Long): List<Long> {
        val popped = masterRedisTemplate.opsForZSet().popMin(WAITING_KEY, count)
        return popped?.mapNotNull { it.value?.toLong() } ?: emptyList()
    }

    companion object {
        private const val WAITING_KEY = "queue:order:waiting"
    }
}
