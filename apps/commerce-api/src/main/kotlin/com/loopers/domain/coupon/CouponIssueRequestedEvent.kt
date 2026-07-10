package com.loopers.domain.coupon

data class CouponIssueRequestedEvent(
    val requestId: String,
    val userId: Long,
    val couponId: Long,
)
