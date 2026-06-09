package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class CouponTest {
    @DisplayName("Coupon 생성")
    @Nested
    inner class Create {
        @DisplayName("정률 쿠폰 생성 조건이 유효하면 정상적으로 생성된다")
        @Test
        fun createsRateCoupon_whenRequiredFieldsAreValid() {
            val expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00")

            val coupon = Coupon(
                name = "신규가입 10% 할인",
                type = DiscountType.RATE,
                discountValue = 10L,
                minOrderAmount = 10_000L,
                expiredAt = expiredAt,
            )

            assertAll(
                { assertThat(coupon.id).isZero() },
                { assertThat(coupon.name).isEqualTo("신규가입 10% 할인") },
                { assertThat(coupon.type).isEqualTo(DiscountType.RATE) },
                { assertThat(coupon.discountValue).isEqualTo(10L) },
                { assertThat(coupon.minOrderAmount).isEqualTo(10_000L) },
                { assertThat(coupon.expiredAt).isEqualTo(expiredAt) },
                { assertThat(coupon.isDeleted).isFalse() },
            )
        }

        @DisplayName("쿠폰명이 비어 있으면 생성할 수 없다")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            val result = assertThrows<CoreException> {
                createCoupon(name = " ")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("최소 주문 금액이 음수이면 생성할 수 없다")
        @Test
        fun throwsBadRequest_whenMinOrderAmountIsNegative() {
            val result = assertThrows<CoreException> {
                createCoupon(minOrderAmount = -1L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("만료일이 현재 시각 이후가 아니면 생성할 수 없다")
        @Test
        fun throwsBadRequest_whenExpiredAtIsNotFuture() {
            val result = assertThrows<CoreException> {
                createCoupon(expiredAt = ZonedDateTime.parse("2000-01-01T00:00:00+09:00"))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("정액 쿠폰 할인 금액이 0 이하이면 생성할 수 없다")
        @Test
        fun throwsBadRequest_whenFixedDiscountValueIsNotPositive() {
            val result = assertThrows<CoreException> {
                createCoupon(type = DiscountType.FIXED, discountValue = 0L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("정률 쿠폰 할인율이 1에서 100 사이가 아니면 생성할 수 없다")
        @Test
        fun throwsBadRequest_whenRateDiscountValueIsOutOfRange() {
            val result = assertThrows<CoreException> {
                createCoupon(type = DiscountType.RATE, discountValue = 101L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    private fun createCoupon(
        name: String = "신규가입 10% 할인",
        type: DiscountType = DiscountType.RATE,
        discountValue: Long = 10L,
        minOrderAmount: Long? = 10_000L,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
    ): Coupon {
        return Coupon(
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
    }
}
