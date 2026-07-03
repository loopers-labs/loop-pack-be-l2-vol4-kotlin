package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class EventCouponTest {
    private val future = LocalDateTime.of(2026, 12, 31, 23, 59, 59)

    @Test
    fun eventCouponExposesFcfsIssueTypeAndDerivedRemainingQuantity() {
        val coupon = EventCoupon(
            name = "선착순 쿠폰",
            type = CouponType.FIXED,
            value = 1000,
            minOrderAmount = null,
            expiredAt = future,
            eventId = 3L,
            totalQuantity = 10,
            issuedQuantity = 4,
        )

        assertAll(
            { assertThat(coupon.getIssueType()).isEqualTo(CouponIssueType.FIRST_COME_FIRST_SERVED) },
            { assertThat(coupon.remainingQuantity).isEqualTo(6) },
            { assertThat(coupon.isExhausted()).isFalse() },
        )
    }

    @Test
    fun eventCouponRejectsInvalidQuantity() {
        val ex = assertThrows<CoreException> {
            EventCoupon(
                name = "잘못된 선착순 쿠폰",
                type = CouponType.FIXED,
                value = 1000,
                minOrderAmount = null,
                expiredAt = future,
                eventId = 3L,
                totalQuantity = 3,
                issuedQuantity = 4,
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
