package com.loopers.coupon.domain

interface UserCouponRepository {
    fun save(userCoupon: UserCoupon): UserCoupon

    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    fun findByUserIdAndCouponId(userId: Long, couponId: Long): UserCoupon?
}
