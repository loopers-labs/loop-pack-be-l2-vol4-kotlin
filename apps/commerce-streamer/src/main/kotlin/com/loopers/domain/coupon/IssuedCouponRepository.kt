package com.loopers.domain.coupon

interface IssuedCouponRepository {
    fun save(model: IssuedCouponModel): IssuedCouponModel
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean
}
