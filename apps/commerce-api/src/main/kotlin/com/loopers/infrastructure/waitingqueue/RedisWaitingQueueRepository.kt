package com.loopers.infrastructure.waitingqueue

import com.loopers.config.redis.RedisConfig
import com.loopers.config.waitingqueue.WaitingQueueProperties
import com.loopers.domain.waitingqueue.WaitingQueuePosition
import com.loopers.domain.waitingqueue.WaitingQueueRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class RedisWaitingQueueRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val properties: WaitingQueueProperties,
) : WaitingQueueRepository {
    private val enqueueScript = DefaultRedisScript<String>().apply {
        setScriptText(ENQUEUE_SCRIPT)
        resultType = String::class.java
    }
    private val consumeAllowedScript = DefaultRedisScript<Long>().apply {
        setScriptText(CONSUME_ALLOWED_SCRIPT)
        resultType = Long::class.java
    }

    override fun shouldEnterQueue(): Boolean {
        val queueSize = redisTemplate.opsForZSet().zCard(QUEUE_KEY) ?: 0
        if (queueSize > 0) {
            return true
        }

        val trafficKey = trafficKey()
        val attempts = redisTemplate.opsForValue().increment(trafficKey) ?: 1
        redisTemplate.expire(trafficKey, TRAFFIC_BUCKET_TTL)
        return attempts > properties.trafficThresholdPerSecond
    }

    override fun enqueue(userId: Long): String {
        val token = java.util.UUID.randomUUID().toString()
        return redisTemplate.execute(
            enqueueScript,
            listOf(QUEUE_KEY, SEQUENCE_KEY, USER_KEY_PREFIX, TOKEN_KEY_PREFIX),
            userId.toString(),
            token,
            properties.tokenTtl.seconds.coerceAtLeast(1).toString(),
        ) ?: token
    }

    override fun getPosition(userId: Long, token: String): WaitingQueuePosition {
        redisTemplate.opsForValue().get(allowedKey(token))?.let { allowedUserId ->
            if (allowedUserId != userId.toString()) {
                throw CoreException(ErrorType.BAD_REQUEST, "대기열 토큰이 현재 사용자와 일치하지 않습니다.")
            }
            return WaitingQueuePosition.Allowed
        }

        val queuedUserId = redisTemplate.opsForHash<String, String>().get(tokenKey(token), USER_ID_FIELD)
            ?: throw CoreException(ErrorType.BAD_REQUEST, "대기열 토큰이 만료되었거나 존재하지 않습니다.")
        if (queuedUserId != userId.toString()) {
            throw CoreException(ErrorType.BAD_REQUEST, "대기열 토큰이 현재 사용자와 일치하지 않습니다.")
        }

        val rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, token)
            ?: throw CoreException(ErrorType.BAD_REQUEST, "대기열 토큰이 대기열에 존재하지 않습니다.")
        return WaitingQueuePosition.Waiting(leftPeople = rank)
    }

    override fun consumeAllowedToken(userId: Long, token: String) {
        val result = redisTemplate.execute(
            consumeAllowedScript,
            listOf(allowedKey(token)),
            userId.toString(),
        ) ?: 0
        when (result) {
            1L -> return
            -1L -> throw CoreException(ErrorType.BAD_REQUEST, "대기열 토큰이 현재 사용자와 일치하지 않습니다.")
            else -> throw CoreException(ErrorType.BAD_REQUEST, "대기열 입장이 아직 허용되지 않았습니다.")
        }
    }

    override fun isAdmissionAlive(): Boolean =
        redisTemplate.opsForValue().get(HEARTBEAT_KEY) != null

    private fun trafficKey(): String =
        "$TRAFFIC_KEY_PREFIX:${Instant.now().epochSecond}"

    companion object {
        const val QUEUE_KEY = "wq:orders:queue"
        const val SEQUENCE_KEY = "wq:orders:seq"
        const val USER_KEY_PREFIX = "wq:orders:user:"
        const val TOKEN_KEY_PREFIX = "wq:orders:token:"
        const val ALLOWED_KEY_PREFIX = "wq:orders:allowed:"
        const val HEARTBEAT_KEY = "wq:orders:heartbeat"
        private const val TRAFFIC_KEY_PREFIX = "wq:orders:traffic"
        private const val USER_ID_FIELD = "userId"
        private val TRAFFIC_BUCKET_TTL = java.time.Duration.ofSeconds(2)

        private fun tokenKey(token: String): String =
            "$TOKEN_KEY_PREFIX$token"

        private fun allowedKey(token: String): String =
            "$ALLOWED_KEY_PREFIX$token"

        private const val ENQUEUE_SCRIPT = """
            local existingToken = redis.call("GET", KEYS[3] .. ARGV[1])
            if existingToken then
              return existingToken
            end

            local sequence = redis.call("INCR", KEYS[2])
            redis.call("ZADD", KEYS[1], sequence, ARGV[2])
            redis.call("SET", KEYS[3] .. ARGV[1], ARGV[2], "EX", tonumber(ARGV[3]))
            redis.call("HSET", KEYS[4] .. ARGV[2], "userId", ARGV[1], "sequence", sequence)
            redis.call("EXPIRE", KEYS[4] .. ARGV[2], tonumber(ARGV[3]))
            return ARGV[2]
        """

        private const val CONSUME_ALLOWED_SCRIPT = """
            local actualUserId = redis.call("GET", KEYS[1])
            if not actualUserId then
              return 0
            end

            if actualUserId ~= ARGV[1] then
              return -1
            end

            redis.call("DEL", KEYS[1])
            return 1
        """
    }
}
