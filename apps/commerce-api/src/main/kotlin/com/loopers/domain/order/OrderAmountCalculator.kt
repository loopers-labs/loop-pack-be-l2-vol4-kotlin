package com.loopers.domain.order

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.DiscountAmount

class OrderAmountCalculator {
    fun calculate(
        items: List<OrderItem>,
        coupon: Coupon? = null,
    ): OrderAmounts {
        val totalAmount = items.fold(OrderAmount.ZERO) { acc, item -> acc + item.totalPrice }
        val discountAmount = coupon
            ?.discountOf(DiscountAmount(totalAmount.amount))
            ?.let { OrderAmount(it.amount) }
            ?: OrderAmount.ZERO

        return OrderAmounts.of(
            totalAmount = totalAmount,
            discountAmount = discountAmount,
        )
    }
}
