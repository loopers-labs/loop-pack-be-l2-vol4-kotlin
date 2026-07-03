package com.loopers.domain.coupon

interface EventCouponRepository {
    fun save(eventCoupon: EventCoupon): EventCoupon

    fun findByCouponId(couponId: Long): EventCoupon?

    fun reserveOneIfAvailable(couponId: Long): Boolean
}
