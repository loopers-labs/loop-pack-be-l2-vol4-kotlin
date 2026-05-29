package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StockReservationTest {
    @Test
    fun confirmChangesActiveToConfirmed() {
        val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)

        reservation.confirm()

        assertThat(reservation.status).isEqualTo(StockReservationStatus.CONFIRMED)
    }

    @Test
    fun cancelChangesActiveToCanceled() {
        val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)

        reservation.cancel()

        assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED)
    }

    @Test
    fun confirmedReservationCannotBeCanceled() {
        val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)
        reservation.confirm()

        val ex = assertThrows<CoreException> {
            reservation.cancel()
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun quantityMustBePositive() {
        val ex = assertThrows<CoreException> {
            StockReservation(orderId = 1L, productId = 10L, quantity = 0)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
