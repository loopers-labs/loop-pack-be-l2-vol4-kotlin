package com.loopers.coupon.domain

interface CouponRepository {
    fun save(coupon: Coupon): Coupon

    fun findById(id: Long): Coupon?

    fun findByIdForUpdate(id: Long): Coupon?
}
