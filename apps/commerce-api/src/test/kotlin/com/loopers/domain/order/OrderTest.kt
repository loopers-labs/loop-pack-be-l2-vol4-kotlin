package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class OrderTest {
    private fun pendingOrder() = Order(
        userId = 1L,
        reservationExpiresAt = LocalDateTime.of(2026, 5, 29, 12, 10),
        deliveryAddress = "서울시 강남구 테헤란로 1",
        deliveryRequest = "문 앞에 놓아주세요",
        phoneNumber = "010-1234-5678",
    )

    @Nested
    inner class Transition {
        @Test
        fun completeChangesPaymentPendingToCompleted() {
            val order = pendingOrder()

            order.complete("payment-1")

            assertAll(
                { assertThat(order.status).isEqualTo(OrderStatus.COMPLETED) },
                { assertThat(order.paymentTransactionId).isEqualTo("payment-1") },
            )
        }

        @Test
        fun paymentFailureKeepsPaymentPending() {
            val order = pendingOrder()

            order.recordPaymentFailure()

            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        }

        @Test
        fun cancelFromPaymentPendingStoresUserReason() {
            val order = pendingOrder()

            order.cancel(OrderCancelReason.USER_REQUESTED)

            assertAll(
                { assertThat(order.status).isEqualTo(OrderStatus.CANCELED) },
                { assertThat(order.cancelReason).isEqualTo(OrderCancelReason.USER_REQUESTED) },
            )
        }

        @Test
        fun cancelFromCompletedStoresUserReason() {
            val order = pendingOrder()
            order.complete("payment-1")

            order.cancel(OrderCancelReason.USER_REQUESTED)

            assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
        }

        @Test
        fun startShippingChangesCompletedToShippingStarted() {
            val order = pendingOrder()
            order.complete("payment-1")

            order.startShipping()

            assertThat(order.status).isEqualTo(OrderStatus.SHIPPING_STARTED)
        }

        @Test
        fun cancelAfterShippingStartedThrowsConflict() {
            val order = pendingOrder()
            order.complete("payment-1")
            order.startShipping()

            val ex = assertThrows<CoreException> {
                order.cancel(OrderCancelReason.USER_REQUESTED)
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }
}
