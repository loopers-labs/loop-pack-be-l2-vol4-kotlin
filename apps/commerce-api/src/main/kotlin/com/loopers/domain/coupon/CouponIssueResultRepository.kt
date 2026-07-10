package com.loopers.domain.coupon

interface CouponIssueResultRepository {
    fun save(result: CouponIssueResult): CouponIssueResult

    fun findByRequestId(requestId: String): CouponIssueResult?

    fun existsSuccess(userId: Long, couponId: Long): Boolean
}
