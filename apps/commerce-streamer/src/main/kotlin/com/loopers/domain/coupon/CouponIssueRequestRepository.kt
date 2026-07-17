package com.loopers.domain.coupon

interface CouponIssueRequestRepository {
    fun findById(id: Long): CouponIssueRequestModel?
    fun save(model: CouponIssueRequestModel): CouponIssueRequestModel
}
