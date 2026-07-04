package com.loopers.domain.coupon.integration

import java.util.UUID

data class CouponIssueAttemptResult(
    val requestId: UUID? = null,
    val error: String? = null,
)
