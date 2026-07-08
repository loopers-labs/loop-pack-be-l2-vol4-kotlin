package com.loopers.interfaces.consumer

import java.time.ZonedDateTime

data class CouponIssuePayload(
    val eventId: String,
    val issueRequestId: Long,
    val userId: Long,
    val couponTemplateId: Long,
    val idempotencyKey: String,
    val occurredAt: ZonedDateTime,
)
