package com.loopers.domain.coupon

interface CouponTemplateRepositoryPort {
    fun save(couponTemplate: CouponTemplate): CouponTemplate
    fun findById(id: Long): CouponTemplate?
}
