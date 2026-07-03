package com.loopers.application.event

data class CouponPublishRequestedApplicationEvent(
    val outboxId: Long,
    val message: CouponPublishRequestedMessage,
)
