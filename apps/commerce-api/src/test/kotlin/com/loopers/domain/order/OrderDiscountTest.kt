package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class OrderDiscountTest {
    private val buyerId = 135135L
    private val couponId = 10L
    private val requestStartedAt = Instant.parse("2026-08-31T14:59:59Z")

    @Test
    fun storesTheConfirmedDiscountSnapshot() {
        val order = Order(buyerId = buyerId, originalAmount = 10_000L)

        order.applyDiscount(couponId = couponId, discountAmount = 1_000L, requestStartedAt = requestStartedAt)
        order.confirm()

        assertAll(
            { assertThat(order.buyerId).isEqualTo(buyerId) },
            { assertThat(order.originalAmount).isEqualTo(10_000L) },
            { assertThat(order.discountAmount).isEqualTo(1_000L) },
            { assertThat(order.finalAmount).isEqualTo(9_000L) },
            { assertThat(order.appliedCouponId).isEqualTo(couponId) },
            { assertThat(order.discountAppliedAt).isEqualTo(requestStartedAt) },
            { assertThat(order.confirmed).isTrue() },
        )
    }

    @Test
    fun allowsZeroAndFullDiscountBoundaries() {
        val zeroDiscount = Order(buyerId = buyerId, originalAmount = 10_000L)
        val fullDiscount = Order(buyerId = buyerId, originalAmount = 10_000L)

        zeroDiscount.applyDiscount(couponId, 0L, requestStartedAt)
        fullDiscount.applyDiscount(couponId, 10_000L, requestStartedAt)

        assertAll(
            { assertThat(zeroDiscount.finalAmount).isEqualTo(10_000L) },
            { assertThat(fullDiscount.finalAmount).isZero() },
        )
    }

    @Test
    fun rejectsNegativeAndExcessDiscountBoundaries() {
        val order = Order(buyerId = buyerId, originalAmount = 10_000L)

        val negative = assertThrows<CoreException> {
            order.applyDiscount(couponId, -1L, requestStartedAt)
        }
        val excess = assertThrows<CoreException> {
            order.applyDiscount(couponId, 10_001L, requestStartedAt)
        }

        assertAll(
            { assertThat(negative.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
            { assertThat(excess.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
        )
    }

    @Test
    fun sameCouponRetryDoesNotAccumulateDiscount() {
        val order = Order(buyerId = buyerId, originalAmount = 10_000L)
        order.applyDiscount(couponId, 1_000L, requestStartedAt)

        order.applyDiscount(couponId, 2_000L, requestStartedAt.plusSeconds(1))

        assertAll(
            { assertThat(order.discountAmount).isEqualTo(1_000L) },
            { assertThat(order.finalAmount).isEqualTo(9_000L) },
            { assertThat(order.discountAppliedAt).isEqualTo(requestStartedAt) },
        )
    }

    @Test
    fun rejectsASecondCouponAndAnyNewCouponAfterConfirmation() {
        val order = Order(buyerId = buyerId, originalAmount = 10_000L)
        order.applyDiscount(couponId, 1_000L, requestStartedAt)

        assertThrows<CoreException> {
            order.applyDiscount(11L, 1_000L, requestStartedAt)
        }

        order.confirm()

        val confirmed = assertThrows<CoreException> {
            order.applyDiscount(12L, 1_000L, requestStartedAt)
        }
        assertThat(confirmed.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
