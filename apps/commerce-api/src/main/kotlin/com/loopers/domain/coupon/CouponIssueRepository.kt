package com.loopers.domain.coupon

interface CouponIssueRepository {
    fun existsByCouponId(couponId: Long): Boolean
}
