package com.loopers.coupon.domain

import com.loopers.shared.domain.Money

object DiscountPolicy {
    fun calculateDiscount(couponType: CouponType, value: Long, price: Long): Money {
        val money = Money(price)
        val raw = when (couponType) {
            CouponType.RATE -> money.amount * value / 100
            CouponType.FIXED -> value
        }
        // 캡: 할인은 주문 금액을 넘지 않는다 (price 를 받으므로 여기서 보장)
        return Money(minOf(raw, money.amount))
    }
}
