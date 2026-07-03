package com.loopers.application.event

import com.loopers.domain.coupon.CouponPublishEventType

data class CouponPublishRequestedMessage(
    val idempotencyKey: String,
    val eventId: Long,
    val couponId: Long,
    val userId: Long,
    val eventType: CouponPublishEventType = CouponPublishEventType.COUPON_PUBLISH_REQUESTED,
)
