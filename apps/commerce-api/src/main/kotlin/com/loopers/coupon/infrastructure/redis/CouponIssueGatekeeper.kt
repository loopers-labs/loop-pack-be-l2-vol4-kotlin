package com.loopers.coupon.infrastructure.redis

import com.loopers.config.redis.RedisConfig
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponRepository
import com.loopers.support.error.ConflictException
import java.time.LocalDateTime
import java.time.ZoneId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

@Component
class CouponIssueGatekeeper(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) private val redisTemplate: RedisTemplate<String, String>,
    val couponRepository: CouponRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun tryPass(couponId: Long, userId: Long) {
        val result = executeOrNull(couponId, userId) ?: return
        if (result == NOT_INITIALIZED) {
            initializeFromDatabase(couponId)
            rejectIfClosed(executeOrNull(couponId, userId) ?: return)
            return
        }
        rejectIfClosed(result)
    }

    fun initialize(couponId: Long, totalQuantity: Long, issuedQuantity: Long, expiredAt: LocalDateTime) {
        try {
            val remaining = (totalQuantity - issuedQuantity).coerceAtLeast(0)
            val stockKey = stockKey(couponId)
            redisTemplate.opsForValue().setIfAbsent(stockKey, remaining.toString())
            redisTemplate.expireAt(stockKey, expiredAt.atZone(ZoneId.systemDefault()).toInstant())
        } catch (e: DataAccessException) {
            logger.warn("쿠폰 발급 게이트 초기화 실패 — lazy 재구성으로 위임. couponId={}, cause={}", couponId, e.javaClass.simpleName)
        }
    }

    private fun rejectIfClosed(result: Long) {
        when (result) {
            ALREADY_REQUESTED -> throw ConflictException(CouponErrorCode.ALREADY_ISSUED)
            SOLD_OUT -> throw ConflictException(CouponErrorCode.SOLD_OUT)
        }
    }

    private fun executeOrNull(couponId: Long, userId: Long): Long? = try {
        redisTemplate.execute(GATE_SCRIPT, listOf(stockKey(couponId), usersKey(couponId)), userId.toString())
    } catch (e: DataAccessException) {
        logger.warn("쿠폰 발급 게이트 실행 실패 — fail-open 통과. couponId={}, cause={}", couponId, e.javaClass.simpleName)
        null
    }

    private fun initializeFromDatabase(couponId: Long) {
        val coupon = couponRepository.findById(couponId) ?: return
        val totalQuantity = coupon.totalQuantity ?: return
        if (coupon.isExpired(LocalDateTime.now())) {
            return
        }
        initialize(couponId, totalQuantity, coupon.issuedQuantity, coupon.expiredAt)
    }

    private fun stockKey(couponId: Long): String = "coupon:$couponId:stock"

    private fun usersKey(couponId: Long): String = "coupon:$couponId:users"

    companion object {
        const val PASS = 1L
        const val SOLD_OUT = 0L
        const val ALREADY_REQUESTED = -1L
        const val NOT_INITIALIZED = -2L

        private val GATE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then return -2 end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return -1 end
            if tonumber(redis.call('GET', KEYS[1])) <= 0 then return 0 end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            local ttl = redis.call('TTL', KEYS[1])
            if ttl > 0 and redis.call('TTL', KEYS[2]) == -1 then redis.call('EXPIRE', KEYS[2], ttl) end
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}
