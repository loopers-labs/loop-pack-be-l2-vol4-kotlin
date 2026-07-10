package com.loopers.batch.waitingqueue

import com.loopers.config.redis.RedisConfig
import com.loopers.config.waitingqueue.WaitingQueueProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "waiting-queue.orders", name = ["enabled"], havingValue = "true")
class WaitingQueueAdmissionWorker(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val properties: WaitingQueueProperties,
) {
    private val admissionScript = DefaultRedisScript<Long>().apply {
        setScriptText(ADMISSION_SCRIPT)
        resultType = Long::class.java
    }

    fun admit(): Long =
        redisTemplate.execute(
            admissionScript,
            listOf(QUEUE_KEY, ALLOWED_KEY_PREFIX, TOKEN_KEY_PREFIX, USER_KEY_PREFIX, HEARTBEAT_KEY),
            properties.admitCount.coerceAtLeast(1).toString(),
            properties.allowedTtl.seconds.coerceAtLeast(1).toString(),
            properties.heartbeatTtl.seconds.coerceAtLeast(1).toString(),
            System.currentTimeMillis().toString(),
        ) ?: 0

    companion object {
        private const val QUEUE_KEY = "wq:orders:queue"
        private const val ALLOWED_KEY_PREFIX = "wq:orders:allowed:"
        private const val TOKEN_KEY_PREFIX = "wq:orders:token:"
        private const val USER_KEY_PREFIX = "wq:orders:user:"
        private const val HEARTBEAT_KEY = "wq:orders:heartbeat"

        private const val ADMISSION_SCRIPT = """
            local tokens = redis.call("ZRANGE", KEYS[1], 0, tonumber(ARGV[1]) - 1)
            local admitted = 0

            for _, token in ipairs(tokens) do
              local tokenKey = KEYS[3] .. token
              local userId = redis.call("HGET", tokenKey, "userId")
              redis.call("ZREM", KEYS[1], token)

              if userId then
                redis.call("SET", KEYS[2] .. token, userId, "EX", tonumber(ARGV[2]))
                redis.call("DEL", tokenKey)
                redis.call("DEL", KEYS[4] .. userId)
                admitted = admitted + 1
              end
            end

            redis.call("SET", KEYS[5], ARGV[4], "EX", tonumber(ARGV[3]))
            return admitted
        """
    }
}
