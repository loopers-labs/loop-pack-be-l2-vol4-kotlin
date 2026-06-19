package com.loopers.domain.coupon

interface IssuedCouponRepository {
    fun save(issuedCoupon: IssuedCoupon): IssuedCoupon

    fun findByUserIdAndCouponId(userId: Long, couponId: Long): IssuedCoupon?

    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    fun findByUserId(userId: Long): List<IssuedCoupon>

    fun findByCouponId(couponId: Long, page: Int, size: Int): List<IssuedCoupon>

    fun markUsedIfAvailable(userId: Long, couponId: Long): Boolean
}
