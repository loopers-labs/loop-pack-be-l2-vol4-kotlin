package com.loopers.application.coupon

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.coupon.CouponIssueResult
import com.loopers.domain.coupon.CouponIssueResultRepository
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.event.EventHandledRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class CouponIssueProcessor(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val couponIssueResultRepository: CouponIssueResultRepository,
    private val eventHandledRepository: EventHandledRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(eventId: String, eventType: String, requestId: String, userId: Long, couponId: Long) {
        if (!claimEvent(eventId, eventType)) return

        val result = couponIssueResultRepository.findByRequestId(requestId) ?: return
        if (!result.isPending()) return

        if (couponIssueResultRepository.existsSuccess(userId, couponId)) {
            result.fail("이미 발급된 쿠폰입니다.")
            couponIssueResultRepository.save(result)
            return
        }

        val coupon = couponRepository.findById(couponId)
        if (coupon == null) {
            result.fail("쿠폰을 찾을 수 없습니다.")
            couponIssueResultRepository.save(result)
            return
        }

        val compensation = RedisIssueCompensation(
            issuedCountKey = couponIssuedCountKey(couponId),
            userIssuedKey = couponUserIssuedKey(couponId = couponId, userId = userId),
        )

        if (!claimUserIssue(compensation)) {
            failAsDuplicated(result, requestId, userId, couponId)
            return
        }
        registerRollbackCompensation(compensation)

        if (coupon.maxIssueCount != null) {
            val issuedCount = redisTemplate.opsForValue().increment(compensation.issuedCountKey) ?: 0
            compensation.issuedCountIncreased = true
            if (!coupon.canIssue(issuedCount)) {
                releaseIssueClaim(compensation)
                result.fail("발급 수량이 초과되었습니다.")
                couponIssueResultRepository.save(result)
                return
            }
        }

        userCouponRepository.saveIssued(UserCoupon(userId = userId, couponId = couponId))
        result.succeed()
        couponIssueResultRepository.save(result)
        log.info("쿠폰 발급 성공: requestId={}, userId={}, couponId={}", requestId, userId, couponId)
    }

    private fun claimUserIssue(compensation: RedisIssueCompensation): Boolean {
        val claimed = redisTemplate.opsForValue()
            .setIfAbsent(compensation.userIssuedKey, "1") ?: false
        compensation.userIssueClaimed = claimed
        return claimed
    }

    private fun registerRollbackCompensation(compensation: RedisIssueCompensation) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    releaseIssueClaim(compensation)
                    log.warn(
                        "트랜잭션 롤백으로 쿠폰 발급 Redis claim 보상: issuedCountKey={}, userIssuedKey={}",
                        compensation.issuedCountKey,
                        compensation.userIssuedKey,
                    )
                }
            }
        })
    }

    private fun releaseIssueClaim(compensation: RedisIssueCompensation) {
        if (compensation.issuedCountIncreased) {
            redisTemplate.opsForValue().decrement(compensation.issuedCountKey)
            compensation.issuedCountIncreased = false
        }
        if (compensation.userIssueClaimed) {
            redisTemplate.delete(compensation.userIssuedKey)
            compensation.userIssueClaimed = false
        }
    }

    private fun failAsDuplicated(
        result: CouponIssueResult,
        requestId: String,
        userId: Long,
        couponId: Long,
    ) {
        result.fail("이미 발급된 쿠폰입니다.")
        couponIssueResultRepository.save(result)
        log.info("중복 쿠폰 발급 요청: requestId={}, userId={}, couponId={}", requestId, userId, couponId)
    }

    private fun claimEvent(eventId: String, eventType: String): Boolean {
        return if (eventHandledRepository.claim(eventId = eventId, eventType = eventType)) {
            true
        } else {
            log.info("이미 처리된 쿠폰 발급 이벤트: eventId={}", eventId)
            false
        }
    }

    companion object {
        fun couponIssuedCountKey(couponId: Long): String = "coupon:$couponId:issued"

        fun couponUserIssuedKey(couponId: Long, userId: Long): String = "coupon:$couponId:user:$userId:issued"
    }

    private data class RedisIssueCompensation(
        val issuedCountKey: String,
        val userIssuedKey: String,
        var issuedCountIncreased: Boolean = false,
        var userIssueClaimed: Boolean = false,
    )
}
