package com.loopers.event

import java.time.ZonedDateTime

data class CouponIssueRequestMessage(
    val eventId: String,
    val requestId: String,
    val couponId: Long,
    val memberId: Long,
    val requestedAt: ZonedDateTime,
)
