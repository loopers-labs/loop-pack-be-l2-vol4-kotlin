package com.loopers.domain.coupon.application.service

import java.util.UUID

data class CouponIssueRequestPayload(
    val requestId: UUID,
    val userId: Long,
    val couponTemplateId: Long,
)
