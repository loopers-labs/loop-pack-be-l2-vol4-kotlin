package com.loopers.domain.order

interface StockReservationRepository {
    fun saveAll(reservations: List<StockReservation>): List<StockReservation>

    fun findByOrderId(orderId: Long): List<StockReservation>

    fun findByOrderIdAndStatus(orderId: Long, status: StockReservationStatus): List<StockReservation>

    fun transitionByOrderId(
        orderId: Long,
        currentStatus: StockReservationStatus,
        nextStatus: StockReservationStatus,
    ): Int
}
