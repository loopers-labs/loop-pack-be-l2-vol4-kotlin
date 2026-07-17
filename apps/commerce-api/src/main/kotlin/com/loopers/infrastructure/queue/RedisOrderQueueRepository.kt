package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.OrderQueueRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

/**
 * Redis Sorted Set 기반 대기열 Repository 구현체.
 *
 * 자료구조:
 * - 대기열: Sorted Set (key: "order:queue", score: timestamp, member: userId)
 * - 입장 토큰: String (key: "order:token:{userId}", value: UUID, TTL: 5분)
 */
@Repository
class RedisOrderQueueRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : OrderQueueRepository {

    /** 대기열에 사용자를 추가한다 (score = 현재 시각 ms). */
    override fun addToQueue(userId: Long) {
        redisTemplate.opsForZSet().add(QUEUE_KEY, userId.toString(), System.currentTimeMillis().toDouble())
    }

    /** 사용자가 이미 대기열에 있는지 확인한다. */
    override fun isInQueue(userId: Long): Boolean {
        return redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString()) != null
    }

    /** 사용자의 현재 순번(0-based rank)을 조회한다. */
    override fun getRank(userId: Long): Long? {
        return redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString())
    }

    /** 전체 대기 인원을 조회한다. */
    override fun getTotalWaiting(): Long {
        return redisTemplate.opsForZSet().zCard(QUEUE_KEY) ?: 0
    }

    /** 대기열 앞에서 N명을 꺼낸다 (ZPOPMIN). */
    override fun popFromQueue(count: Int): List<Long> {
        val results = redisTemplate.opsForZSet().popMin(QUEUE_KEY, count.toLong())
            ?: return emptyList()
        return results.mapNotNull { it.value?.toLongOrNull() }
    }

    /** 입장 토큰을 발급한다 (TTL 5분). */
    override fun issueToken(userId: Long): String {
        val token = UUID.randomUUID().toString()
        redisTemplate.opsForValue().set(
            tokenKey(userId),
            token,
            TOKEN_TTL,
        )
        return token
    }

    /** 사용자의 토큰을 조회한다. */
    override fun getToken(userId: Long): String? {
        return redisTemplate.opsForValue().get(tokenKey(userId))
    }

    /** 토큰을 삭제한다 (주문 완료 후). */
    override fun deleteToken(userId: Long) {
        redisTemplate.delete(tokenKey(userId))
    }

    private fun tokenKey(userId: Long) = "$TOKEN_KEY_PREFIX$userId"

    companion object {
        private const val QUEUE_KEY = "order:queue"
        private const val TOKEN_KEY_PREFIX = "order:token:"
        private val TOKEN_TTL = Duration.ofMinutes(5)
    }
}
