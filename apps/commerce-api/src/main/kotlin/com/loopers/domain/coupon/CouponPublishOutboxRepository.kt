package com.loopers.domain.coupon

import java.time.ZonedDateTime

interface CouponPublishOutboxRepository {
    fun save(outbox: CouponPublishOutbox): CouponPublishOutbox

    fun saveAndFlush(outbox: CouponPublishOutbox): CouponPublishOutbox

    fun existsSuccessfulRequest(eventType: CouponPublishEventType, couponId: Long, userId: Long): Boolean

    fun markPublished(outboxId: Long, publishedAt: ZonedDateTime): Boolean
}
