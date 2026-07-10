package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.EntryTokenRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Redis 기반 입장 토큰 저장소.
 *
 * 토큰 검증-소비는 Lua 스크립트로 원자화한다. GET → 비교 → DEL 을 순차로 하면 동시 요청이
 * 같은 토큰으로 모두 통과해 1토큰=1주문(back-pressure) 이 깨지기 때문이다.
 * master-replica 구성(비클러스터)이라 멀티 키 스크립트에 슬롯 제약이 없다.
 */
@Repository
class EntryTokenRepositoryImpl(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) redisTemplate: RedisTemplate<*, *>,
) : EntryTokenRepository {
    @Suppress("UNCHECKED_CAST")
    private val redis = redisTemplate as RedisTemplate<String, String>

    override fun save(userId: Long, token: String, expiresAt: Instant) {
        redis.opsForValue().set(tokenKey(userId), token)
        redis.expireAt(tokenKey(userId), expiresAt)
        redis.opsForZSet().add(ACTIVE_KEY, userId.toString(), expiresAt.toEpochMilli().toDouble())
    }

    override fun consume(userId: Long, token: String): Boolean {
        val consumed = redis.execute(
            CONSUME_SCRIPT,
            listOf(tokenKey(userId), ACTIVE_KEY),
            token,
            userId.toString(),
        )
        return consumed == 1L
    }

    override fun activeCount(now: Instant): Long {
        redis.opsForZSet().removeRangeByScore(ACTIVE_KEY, Double.NEGATIVE_INFINITY, now.toEpochMilli().toDouble())
        return redis.opsForZSet().zCard(ACTIVE_KEY) ?: 0L
    }

    override fun find(userId: Long): String? =
        redis.opsForValue().get(tokenKey(userId))

    private fun tokenKey(userId: Long): String = "$TOKEN_KEY_PREFIX$userId"

    companion object {
        private const val TOKEN_KEY_PREFIX = "entry-token:"
        private const val ACTIVE_KEY = "active-tokens"

        // KEYS[1]=entry-token:{userId}, KEYS[2]=active-tokens / ARGV[1]=token, ARGV[2]=userId
        // 토큰이 일치할 때만 삭제 + 활성 집합에서 제거. 틀린 토큰엔 손대지 않는다(griefing 방지).
        private val CONSUME_SCRIPT: RedisScript<Long> = RedisScript.of(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('ZREM', KEYS[2], ARGV[2])
                return 1
            else
                return 0
            end
            """.trimIndent(),
            Long::class.java,
        )
    }
}
