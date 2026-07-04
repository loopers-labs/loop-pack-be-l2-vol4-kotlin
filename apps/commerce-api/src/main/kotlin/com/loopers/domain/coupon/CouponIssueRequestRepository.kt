package com.loopers.domain.coupon

interface CouponIssueRequestRepository {
    fun save(couponIssueRequestModel: CouponIssueRequestModel): CouponIssueRequestModel

    fun findByRequestId(requestId: String): CouponIssueRequestModel?
}
