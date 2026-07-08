package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.WaitingQueueRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisWaitingQueueRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : WaitingQueueRepository {
    override fun enterIfAbsent(memberId: Long, score: Double) {
        val member = memberId.toString()
        if (redisTemplate.opsForZSet().score(WAITING_QUEUE_KEY, member) == null) {
            redisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, member, score)
        }
    }

    override fun rank(memberId: Long): Long? {
        return redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, memberId.toString())
    }

    override fun count(): Long {
        return redisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY) ?: 0
    }

    override fun popNext(count: Long): List<Long> {
        if (count <= 0) {
            return emptyList()
        }

        return redisTemplate.opsForZSet()
            .popMin(WAITING_QUEUE_KEY, count)
            ?.mapNotNull { it.value?.toLongOrNull() }
            ?: emptyList()
    }

    private companion object {
        private const val WAITING_QUEUE_KEY = "queue:waiting"
    }
}
