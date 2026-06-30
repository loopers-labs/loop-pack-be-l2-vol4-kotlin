package com.loopers.infrastructure.payment

import com.loopers.config.redis.RedisConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class PaymentRequestLockRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) {
    fun acquireIdempotencyLock(memberId: Long, idempotencyKey: String): Boolean {
        return redisTemplate.opsForValue()
            .setIfAbsent(idempotencyLockKey(memberId, idempotencyKey), "1", IDEMPOTENCY_LOCK_TTL) == true
    }

    fun releaseIdempotencyLock(memberId: Long, idempotencyKey: String) {
        redisTemplate.delete(idempotencyLockKey(memberId, idempotencyKey))
    }

    fun acquireOrderLock(orderId: Long): Boolean {
        return redisTemplate.opsForValue()
            .setIfAbsent(orderLockKey(orderId), "1", ORDER_LOCK_TTL) == true
    }

    fun releaseOrderLock(orderId: Long) {
        redisTemplate.delete(orderLockKey(orderId))
    }

    private fun idempotencyLockKey(memberId: Long, idempotencyKey: String): String {
        return "payment:idempotency-lock:member:$memberId:key:$idempotencyKey"
    }

    private fun orderLockKey(orderId: Long): String {
        return "payment:order-lock:$orderId"
    }

    private companion object {
        private val IDEMPOTENCY_LOCK_TTL: Duration = Duration.ofSeconds(30)
        private val ORDER_LOCK_TTL: Duration = Duration.ofSeconds(10)
    }
}
