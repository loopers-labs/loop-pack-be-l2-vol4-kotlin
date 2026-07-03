package com.loopers.domain.coupon

interface CouponIssueRequestRepository {
    fun save(request: CouponIssueRequest): CouponIssueRequest
    fun findByRequestId(requestId: String): CouponIssueRequest?
}
