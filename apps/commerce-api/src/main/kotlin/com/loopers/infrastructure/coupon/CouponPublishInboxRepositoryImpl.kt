package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponPublishInbox
import com.loopers.domain.coupon.CouponPublishInboxRepository
import org.springframework.stereotype.Component

@Component
class CouponPublishInboxRepositoryImpl(
    private val couponPublishInboxJpaRepository: CouponPublishInboxJpaRepository,
) : CouponPublishInboxRepository {
    override fun saveAndFlush(inbox: CouponPublishInbox): CouponPublishInbox =
        couponPublishInboxJpaRepository.saveAndFlush(inbox)

    override fun existsByIdempotencyKey(idempotencyKey: String): Boolean =
        couponPublishInboxJpaRepository.existsByIdempotencyKeyAndDeletedAtIsNull(idempotencyKey)
}
