package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StockReservationTest {
    @Test
    fun completeChangesInProgressToCompleted() {
        val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)

        reservation.complete()

        assertThat(reservation.status).isEqualTo(StockReservationStatus.COMPLETED)
    }

    @Test
    fun expireChangesInProgressToExpired() {
        val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)

        reservation.expire()

        assertThat(reservation.status).isEqualTo(StockReservationStatus.EXPIRED)
    }

    @Test
    fun cancelChangesInProgressToCanceled() {
        val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)

        reservation.cancel()

        assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED)
    }

    @Test
    fun completedReservationCanBeCanceledAfterPaymentCancel() {
        val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)
        reservation.complete()

        reservation.cancel()

        assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED)
    }

    @Test
    fun quantityMustBePositive() {
        val ex = assertThrows<CoreException> {
            StockReservation(orderId = 1L, productId = 10L, quantity = 0)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
