package com.loopers.coupon.domain

interface CouponIssueResultRepository {
    fun save(couponIssueResult: CouponIssueResult): CouponIssueResult

    fun findById(requestId: String): CouponIssueResult?
}
