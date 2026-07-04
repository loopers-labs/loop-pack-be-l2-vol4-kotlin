package com.loopers.domain.coupon.model

enum class CouponIssueRequestStatus {
    PENDING,
    ISSUED,
    DUPLICATE,
    SOLD_OUT,
    FAILED,
}
