package com.loopers.domain.order

interface StockReservationRepository {
    fun saveAll(reservations: List<StockReservation>): List<StockReservation>

    fun findByOrderId(orderId: Long): List<StockReservation>

    fun sumActiveQuantityByProductId(productId: Long): Int

    fun sumActiveQuantityByProductIdForUpdate(productId: Long): Int

    fun cancelActiveByOrderId(orderId: Long): Int

    fun confirmActiveByOrderId(orderId: Long): Int
}
