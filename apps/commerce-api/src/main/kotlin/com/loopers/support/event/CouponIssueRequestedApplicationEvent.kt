package com.loopers.support.event

import java.time.ZonedDateTime
import java.util.UUID

data class CouponIssueRequestedApplicationEvent(
    val requestId: UUID,
    val requestAggregateId: Long,
    val userId: Long,
    val couponTemplateId: Long,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
