package com.loopers.domain.coupon

import com.loopers.domain.shared.Money

object DiscountPolicy {
    fun calculateDiscount(couponType: CouponType, value: Long, price: Money): Money {
        val raw = when (couponType) {
            CouponType.RATE -> price.amount * value / 100
            CouponType.FIXED -> value
        }
        // 캡: 할인은 주문 금액을 넘지 않는다 (price 를 받으므로 여기서 보장)
        return Money(minOf(raw, price.amount))
    }
}

