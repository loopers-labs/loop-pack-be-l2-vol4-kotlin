package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.EntryTokenRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisEntryTokenRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
) : EntryTokenRepository {

    companion object {
        private const val KEY_PREFIX = "queue:token:"
    }

    override fun issue(loginId: String, token: String, ttl: Duration) {
        masterTemplate.opsForValue().set(KEY_PREFIX + loginId, token, ttl)
    }

    override fun find(loginId: String): String? {
        return masterTemplate.opsForValue().get(KEY_PREFIX + loginId)
    }

    override fun delete(loginId: String) {
        masterTemplate.delete(KEY_PREFIX + loginId)
    }
}
