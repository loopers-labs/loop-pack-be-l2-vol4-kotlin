package com.loopers.coupon.domain

import com.loopers.shared.domain.Money
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class DiscountPolicyTest {
    @ParameterizedTest
    @MethodSource("discountCases")
    @DisplayName("적합한 CouponType 을 제공하면 그에 상응하는 할인율을 제공한다")
    fun giveAppropriateTest(type: CouponType, value: Long, price: Money, expectedMoney: Money) {
        Assertions.assertThat(DiscountPolicy.calculateDiscount(couponType = type, value = value, price)).isEqualTo(expectedMoney)
    }

    @Test
    @DisplayName("할인 금액이 주문 금액을 초과하면 주문 금액까지만 할인한다")
    fun capsDiscountAtPrice_whenDiscountExceedsOrderAmount() {
        val discount = DiscountPolicy.calculateDiscount(couponType = CouponType.FIXED, value = 15000L, price = Money(10000))

        Assertions.assertThat(discount).isEqualTo(Money(10000))
    }

    companion object {
        @JvmStatic
        fun discountCases() = listOf(
            Arguments.of(CouponType.RATE, 10L, Money(10000), Money(1000)),
            Arguments.of(CouponType.FIXED, 2000L, Money(10000), Money(2000)),
        )
    }
}
