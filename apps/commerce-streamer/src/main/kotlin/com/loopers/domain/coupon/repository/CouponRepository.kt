package com.loopers.domain.coupon.repository

import com.loopers.domain.coupon.model.Coupon

interface CouponRepository {
    fun findByIdForUpdate(couponId: Long): Coupon?

    fun update(coupon: Coupon): Coupon
}
