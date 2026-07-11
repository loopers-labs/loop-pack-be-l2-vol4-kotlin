package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.WaitingQueueRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisWaitingQueueRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>
) : WaitingQueueRepository {
    override fun addIfAbsent(loginId: String, enteredAtMillis: Long): Boolean {
        return masterTemplate.opsForZSet()
            .addIfAbsent(KEY, loginId, enteredAtMillis.toDouble()) ?: false
    }

    override fun rank(loginId: String): Long? {
        return masterTemplate.opsForZSet().rank(KEY, loginId)
    }

    override fun size(): Long {
        return masterTemplate.opsForZSet().size(KEY) ?: 0L
    }

    override fun peekNext(count: Int): List<String> {
        if (count <= 0) return emptyList()
        return masterTemplate.opsForZSet().range(KEY, 0, count - 1L)?.toList() ?: emptyList()
    }

    override fun remove(loginIds: List<String>) {
        if (loginIds.isEmpty()) return
        masterTemplate.opsForZSet().remove(KEY, *loginIds.toTypedArray())
    }

    companion object {
        private const val KEY = "queue:waiting"
    }
}
