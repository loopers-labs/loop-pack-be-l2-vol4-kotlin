package com.loopers.domain.coupon

import org.springframework.data.domain.Page

interface CouponRepository {
    fun findById(couponId: Long): Coupon?

    fun findDisplayable(page: Int, size: Int): Page<Coupon>

    fun save(coupon: Coupon): Coupon

    fun existsByName(name: String): Boolean
}
