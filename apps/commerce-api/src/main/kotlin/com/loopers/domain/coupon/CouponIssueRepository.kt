package com.loopers.domain.coupon

interface CouponIssueRepository {
    fun save(issue: CouponIssue): CouponIssue

    fun findById(issueId: Long): CouponIssue?

    fun existsByCouponId(couponId: Long): Boolean
}
