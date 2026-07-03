package com.loopers.application.event

import com.loopers.domain.coupon.CouponPublishInbox
import com.loopers.domain.coupon.CouponPublishInboxRepository
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponPublishConsumerService(
    private val inboxRepository: CouponPublishInboxRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
) {
    @Transactional
    fun process(message: CouponPublishRequestedMessage) {
        if (inboxRepository.existsByIdempotencyKey(message.idempotencyKey)) return

        inboxRepository.saveAndFlush(
            CouponPublishInbox(
                idempotencyKey = message.idempotencyKey,
                eventType = message.eventType,
                couponId = message.couponId,
                userId = message.userId,
            ),
        )

        if (!issuedCouponRepository.existsByUserIdAndCouponId(message.userId, message.couponId)) {
            issuedCouponRepository.save(IssuedCoupon(userId = message.userId, couponId = message.couponId))
        }
    }
}
