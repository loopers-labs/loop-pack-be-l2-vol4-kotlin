package com.loopers.domain.coupon.repository

import com.loopers.domain.coupon.model.CouponIssue

interface CouponIssueRepository {
    fun save(issue: CouponIssue): CouponIssue

    fun existsByCouponIdAndMemberId(couponId: Long, memberId: Long): Boolean
}
