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
        fun completeChangesPaymentPendingToCompletedWithoutPaymentTransactionOnOrder() {
            val order = pendingOrder()

            order.complete()

            assertThat(order.status).isEqualTo(OrderStatus.COMPLETED)
        }

        @Test
        fun markCompletionFailedChangesPaymentPendingToFailed() {
            val order = pendingOrder()

            order.markCompletionFailed()

            assertThat(order.status).isEqualTo(OrderStatus.FAILED)
        }

        @Test
        fun expireChangesPaymentPendingToExpired() {
            val order = pendingOrder()

            order.expire()

            assertThat(order.status).isEqualTo(OrderStatus.EXPIRED)
        }

        @Test
        fun failedOrderCannotBeCanceledByUser() {
            val order = pendingOrder()
            order.markCompletionFailed()

            val ex = assertThrows<CoreException> {
                order.cancelByUser()
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @Test
        fun completedOrderCanBeCanceledBeforeShipping() {
            val order = pendingOrder()
            order.complete()

            order.cancelByUser()

            assertAll(
                { assertThat(order.status).isEqualTo(OrderStatus.CANCELED) },
                { assertThat(order.cancelReason).isEqualTo(OrderCancelReason.USER_REQUESTED) },
            )
        }
    }
}
