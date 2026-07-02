package com.loopers.domain.coupon.vo

import com.loopers.domain.product.vo.Money

sealed interface DiscountPolicy {
    val couponType: CouponType

    fun calculate(totalPrice: Money): Money
}
