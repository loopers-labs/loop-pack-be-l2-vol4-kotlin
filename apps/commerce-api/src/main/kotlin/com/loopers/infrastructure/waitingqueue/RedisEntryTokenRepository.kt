package com.loopers.infrastructure.waitingqueue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.waitingqueue.EntryTokenRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisEntryTokenRepository(
    @param:Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : EntryTokenRepository {
    override fun issue(memberId: Long, token: String, ttl: Duration) {
        redisTemplate.opsForValue().set(tokenKey(memberId), token, ttl)
    }

    override fun find(memberId: Long): String? {
        return redisTemplate.opsForValue().get(tokenKey(memberId))
    }

    override fun delete(memberId: Long) {
        redisTemplate.delete(tokenKey(memberId))
    }

    private fun tokenKey(memberId: Long): String {
        return "queue:entry-token:$memberId"
    }
}
