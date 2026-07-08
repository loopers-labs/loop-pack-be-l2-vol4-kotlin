package com.loopers.domain.coupon

interface CouponIssueRequestRepositoryPort {
    fun save(request: CouponIssueRequest): CouponIssueRequest
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean
    fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequest?
}
