package com.loopers.domain.coupon

interface CouponRepository {
    fun save(coupon: Coupon): Coupon

    fun findById(couponId: Long): Coupon?

    fun findAll(page: Int, size: Int): List<Coupon>
}
