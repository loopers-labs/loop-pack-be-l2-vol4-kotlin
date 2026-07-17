package com.loopers.domain.coupon

interface CouponTemplateRepository {
    fun findByIdWithLock(id: Long): CouponTemplateModel?
    fun save(model: CouponTemplateModel): CouponTemplateModel
}
