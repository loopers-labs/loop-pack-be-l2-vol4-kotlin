package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.EntryToken
import com.loopers.domain.queue.EntryTokenRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class EntryTokenRepositoryImpl(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
    @Value("\${queue.entry-token.ttl-seconds:300}")
    private val ttlSeconds: Long,
) : EntryTokenRepository {
    override fun save(userId: Long, token: EntryToken) {
        masterRedisTemplate.opsForValue().set(tokenKey(userId), token.value, Duration.ofSeconds(ttlSeconds))
    }

    override fun find(userId: Long): EntryToken? {
        return masterRedisTemplate.opsForValue().get(tokenKey(userId))?.let { EntryToken(it) }
    }

    override fun delete(userId: Long) {
        masterRedisTemplate.delete(tokenKey(userId))
    }

    private fun tokenKey(userId: Long): String = "$TOKEN_KEY_PREFIX:$userId"

    companion object {
        private const val TOKEN_KEY_PREFIX = "queue:entry-token"
    }
}
