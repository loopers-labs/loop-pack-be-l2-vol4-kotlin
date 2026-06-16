package com.loopers.domain.coupon

import com.loopers.domain.coupon.enums.DiscountType
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class CouponPolicyTest {
    @DisplayName("쿠폰 정책")
    @Nested
    inner class Validate {
        @DisplayName("쿠폰명이 비어 있으면 유효하지 않다")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            val result = assertThrows<CoreException> {
                validate(name = " ")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("최소 주문 금액이 음수이면 유효하지 않다")
        @Test
        fun throwsBadRequest_whenMinOrderAmountIsNegative() {
            val result = assertThrows<CoreException> {
                validate(minOrderAmount = -1L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("만료일이 현재 시각 이후가 아니면 유효하지 않다")
        @Test
        fun throwsBadRequest_whenExpiredAtIsNotFuture() {
            val result = assertThrows<CoreException> {
                validate(expiredAt = ZonedDateTime.parse("2000-01-01T00:00:00+09:00"))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("정액 쿠폰 할인 금액이 0 이하이면 유효하지 않다")
        @Test
        fun throwsBadRequest_whenFixedDiscountValueIsNotPositive() {
            val result = assertThrows<CoreException> {
                validate(type = DiscountType.FIXED, discountValue = 0L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("정률 쿠폰 할인율이 1에서 100 사이가 아니면 유효하지 않다")
        @Test
        fun throwsBadRequest_whenRateDiscountValueIsOutOfRange() {
            val result = assertThrows<CoreException> {
                validate(type = DiscountType.RATE, discountValue = 101L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    private fun validate(
        name: String = "신규가입 10% 할인",
        type: DiscountType = DiscountType.RATE,
        discountValue: Long = 10L,
        minOrderAmount: Long? = 10_000L,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
    ) {
        CouponPolicy.validate(
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
    }
}
