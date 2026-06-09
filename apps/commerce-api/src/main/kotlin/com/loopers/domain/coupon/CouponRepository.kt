package com.loopers.domain.coupon

interface CouponRepository {
    fun save(coupon: Coupon): Coupon

    fun existsByName(name: String): Boolean
}
