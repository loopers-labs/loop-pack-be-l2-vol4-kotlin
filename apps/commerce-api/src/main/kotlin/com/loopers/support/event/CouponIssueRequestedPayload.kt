package com.loopers.support.event

import java.util.UUID

internal data class CouponIssueRequestedPayload(
    val requestId: UUID,
    val userId: Long,
    val couponTemplateId: Long,
)
