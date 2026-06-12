package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class CouponTest {
    private val future = LocalDateTime.of(2026, 12, 31, 23, 59, 59)

    @Test
    fun fixedCouponDiscountIsCappedAtOrderAmount() {
        val coupon = Coupon(
            name = "5000원 할인",
            type = CouponType.FIXED,
            value = 5000,
            minOrderAmount = null,
            expiredAt = future,
        )

        assertAll(
            { assertThat(coupon.calculateDiscount(12000)).isEqualTo(5000) },
            { assertThat(coupon.calculateDiscount(3000)).isEqualTo(3000) },
        )
    }

    @Test
    fun rateCouponUsesIntegerPercentDiscount() {
        val coupon = Coupon(
            name = "15% 할인",
            type = CouponType.RATE,
            value = 15,
            minOrderAmount = 10000,
            expiredAt = future,
        )

        val discount = coupon.calculateDiscount(12900)

        assertThat(discount).isEqualTo(1935)
    }

    @Test
    fun couponRejectsOrderBelowMinimumAmount() {
        val coupon = Coupon(
            name = "최소금액 쿠폰",
            type = CouponType.FIXED,
            value = 3000,
            minOrderAmount = 10000,
            expiredAt = future,
        )

        val ex = assertThrows<CoreException> {
            coupon.calculateDiscount(9900)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun rateCouponRejectsPercentOutsideOneToHundred() {
        val ex = assertThrows<CoreException> {
            Coupon(
                name = "잘못된 정률",
                type = CouponType.RATE,
                value = 101,
                minOrderAmount = null,
                expiredAt = future,
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @Test
    fun expiredAtIsExclusiveUsableBoundary() {
        val coupon = Coupon(
            name = "오늘까지",
            type = CouponType.FIXED,
            value = 1000,
            minOrderAmount = null,
            expiredAt = LocalDateTime.of(2026, 6, 12, 12, 0),
        )

        assertAll(
            { assertThat(coupon.isExpired(LocalDateTime.of(2026, 6, 12, 11, 59, 59))).isFalse() },
            { assertThat(coupon.isExpired(LocalDateTime.of(2026, 6, 12, 12, 0))).isTrue() },
        )
    }
}
