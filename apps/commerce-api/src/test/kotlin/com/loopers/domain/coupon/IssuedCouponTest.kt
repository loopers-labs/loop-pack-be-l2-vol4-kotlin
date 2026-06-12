package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class IssuedCouponTest {
    @Test
    fun issuedCouponStartsAvailableAndCanBeUsedOnce() {
        val issuedCoupon = IssuedCoupon(userId = 1L, couponId = 10L)

        issuedCoupon.markUsed()

        assertThat(issuedCoupon.status).isEqualTo(IssuedCouponStatus.USED)
    }

    @Test
    fun usedCouponCannotBeUsedAgain() {
        val issuedCoupon = IssuedCoupon(userId = 1L, couponId = 10L)
        issuedCoupon.markUsed()

        val ex = assertThrows<CoreException> {
            issuedCoupon.markUsed()
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun availableCouponReportsExpiredEffectiveStatusAfterTemplateExpiration() {
        val coupon = Coupon(
            name = "기간 쿠폰",
            type = CouponType.FIXED,
            value = 1000,
            minOrderAmount = null,
            expiredAt = LocalDateTime.of(2026, 6, 12, 12, 0),
        )
        val issuedCoupon = IssuedCoupon(userId = 1L, couponId = 10L)

        assertAll(
            {
                assertThat(
                    issuedCoupon.effectiveStatus(coupon, LocalDateTime.of(2026, 6, 12, 11, 59)),
                ).isEqualTo(IssuedCouponStatus.AVAILABLE)
            },
            {
                assertThat(
                    issuedCoupon.effectiveStatus(coupon, LocalDateTime.of(2026, 6, 12, 12, 0)),
                ).isEqualTo(IssuedCouponStatus.EXPIRED)
            },
        )
    }
}
