package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponPublishEventType
import com.loopers.domain.coupon.CouponPublishOutbox
import com.loopers.domain.coupon.CouponPublishOutboxRepository
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class CouponPublishOutboxRepositoryImpl(
    private val couponPublishOutboxJpaRepository: CouponPublishOutboxJpaRepository,
) : CouponPublishOutboxRepository {
    override fun save(outbox: CouponPublishOutbox): CouponPublishOutbox =
        couponPublishOutboxJpaRepository.save(outbox)

    override fun saveAndFlush(outbox: CouponPublishOutbox): CouponPublishOutbox =
        couponPublishOutboxJpaRepository.saveAndFlush(outbox)

    override fun existsSuccessfulRequest(eventType: CouponPublishEventType, couponId: Long, userId: Long): Boolean =
        couponPublishOutboxJpaRepository.existsByEventTypeAndCouponIdAndUserIdAndDeletedAtIsNull(eventType, couponId, userId)

    override fun markPublished(outboxId: Long, publishedAt: ZonedDateTime): Boolean =
        couponPublishOutboxJpaRepository.markPublished(outboxId, publishedAt) == 1
}
