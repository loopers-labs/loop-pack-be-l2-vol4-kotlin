package com.loopers.domain.coupon

import com.loopers.domain.shared.Money
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
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


    companion object {
        @JvmStatic
        fun discountCases() = listOf(
            Arguments.of(CouponType.RATE, 10L, Money(10000), Money(1000)),
            Arguments.of(CouponType.FIXED, 2000L, Money(10000), Money(2000)),
        )
    }
}
