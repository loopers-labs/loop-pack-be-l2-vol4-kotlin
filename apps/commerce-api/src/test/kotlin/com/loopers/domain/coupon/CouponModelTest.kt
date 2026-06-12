package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.ZonedDateTime

class CouponModelTest {
    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("정률 쿠폰의 할인율이 1~100 범위를 벗어나면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenRateIsOutOfRange() {
            listOf(BigDecimal.ZERO, BigDecimal("101")).forEach { rate ->
                val exception = assertThrows<CoreException> { coupon(type = CouponType.RATE, discountValue = rate) }
                assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            }
        }

        @DisplayName("정액 쿠폰의 할인 금액이 0 이하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenFixedAmountIsNotPositive() {
            val exception = assertThrows<CoreException> { coupon(type = CouponType.FIXED, discountValue = BigDecimal.ZERO) }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            val exception = assertThrows<CoreException> { coupon(name = " ") }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("최소 주문 금액이 0 이하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenMinOrderAmountIsNotPositive() {
            val exception = assertThrows<CoreException> { coupon(minOrderAmount = BigDecimal.ZERO) }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    inner class CalculateDiscount {
        @DisplayName("정액 쿠폰은 할인 금액을 그대로 반환하되 주문 금액을 초과하지 않는다.")
        @Test
        fun fixedDiscountIsCappedAtOrderAmount() {
            val fixed = coupon(type = CouponType.FIXED, discountValue = BigDecimal("5000"))

            assertThat(fixed.calculateDiscount(BigDecimal("20000"))).isEqualByComparingTo(BigDecimal("5000"))
            assertThat(fixed.calculateDiscount(BigDecimal("3000"))).isEqualByComparingTo(BigDecimal("3000"))
        }

        @DisplayName("정률 쿠폰은 주문 금액의 비율만큼 할인하고, 소수점 둘째 자리에서 내림한다.")
        @Test
        fun rateDiscountIsPercentageRoundedDown() {
            val rate = coupon(type = CouponType.RATE, discountValue = BigDecimal("10"))

            assertThat(rate.calculateDiscount(BigDecimal("27000"))).isEqualByComparingTo(BigDecimal("2700.00"))
            assertThat(rate.calculateDiscount(BigDecimal("99.99"))).isEqualByComparingTo(BigDecimal("9.99"))
        }

        @DisplayName("최소 주문 금액 미달이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenOrderAmountIsBelowMinimum() {
            val withMinimum = coupon(minOrderAmount = BigDecimal("10000"))

            val exception = assertThrows<CoreException> { withMinimum.calculateDiscount(BigDecimal("9999")) }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("만료 여부를 판단할 때, expiredAt이 기준 시각보다 과거면 만료다.")
    @Test
    fun isExpired_whenExpiredAtIsBeforeNow() {
        val now = ZonedDateTime.now()
        assertThat(coupon(expiredAt = now.minusSeconds(1)).isExpired(now)).isTrue()
        assertThat(coupon(expiredAt = now.plusDays(1)).isExpired(now)).isFalse()
    }

    private fun coupon(
        name: String = "테스트 쿠폰",
        type: CouponType = CouponType.FIXED,
        discountValue: BigDecimal = BigDecimal("1000"),
        minOrderAmount: BigDecimal? = null,
        expiredAt: ZonedDateTime = ZonedDateTime.now().plusDays(30),
    ) = CouponModel(
        name = name,
        type = type,
        discountValue = discountValue,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
    )
}
