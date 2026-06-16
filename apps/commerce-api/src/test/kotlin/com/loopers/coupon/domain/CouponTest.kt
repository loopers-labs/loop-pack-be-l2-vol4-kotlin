package com.loopers.coupon.domain

import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CouponTest {
    @DisplayName("할인 값이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenValueIsNotPositive() {
        val result = assertThrows<BadRequestException> {
            coupon(type = CouponType.FIXED, value = 0)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.INVALID_DISCOUNT_VALUE)
    }

    @DisplayName("정률 쿠폰의 할인 값이 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenRateValueExceeds100() {
        val result = assertThrows<BadRequestException> {
            coupon(type = CouponType.RATE, value = 101)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.RATE_DISCOUNT_OUT_OF_RANGE)
    }

    @DisplayName("주문 금액이 최소 주문 금액보다 적으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenOrderAmountBelowMinimum() {
        val coupon = coupon(minOrderAmount = Money(10000))

        val result = assertThrows<BadRequestException> {
            coupon.validateUsable(orderAmount = Money(9999), now = NOW)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.MIN_ORDER_NOT_MET)
    }

    @DisplayName("만료된 쿠폰을 사용하면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenCouponExpired() {
        val coupon = coupon(expiredAt = NOW.minusDays(1))

        val result = assertThrows<BadRequestException> {
            coupon.validateUsable(orderAmount = Money(10000), now = NOW)
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.EXPIRED)
    }

    @DisplayName("최소 주문 금액을 충족하고 만료되지 않았으면 검증을 통과한다.")
    @Test
    fun passesValidation_whenAmountMeetsMinimumAndNotExpired() {
        val coupon = coupon(minOrderAmount = Money(10000), expiredAt = NOW.plusDays(1))

        assertThatCode {
            coupon.validateUsable(orderAmount = Money(10000), now = NOW)
        }.doesNotThrowAnyException()
    }

    private fun coupon(
        type: CouponType = CouponType.FIXED,
        value: Long = 1000,
        minOrderAmount: Money = Money(0),
        expiredAt: LocalDateTime = NOW.plusDays(1),
    ): Coupon = Coupon(
        type = type,
        name = "테스트쿠폰",
        value = value,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
        createdBy = 1L,
    )

    private companion object {
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
