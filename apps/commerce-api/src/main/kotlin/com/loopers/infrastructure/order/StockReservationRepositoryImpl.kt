package com.loopers.infrastructure.order

import com.loopers.application.catalog.port.OrderReservationQueryPort
import com.loopers.domain.order.StockReservation
import com.loopers.domain.order.StockReservationRepository
import com.loopers.domain.order.StockReservationStatus
import org.springframework.stereotype.Component

@Component
class StockReservationRepositoryImpl(
    private val stockReservationJpaRepository: StockReservationJpaRepository,
) : StockReservationRepository,
    OrderReservationQueryPort {
    override fun saveAll(reservations: List<StockReservation>): List<StockReservation> =
        stockReservationJpaRepository.saveAll(reservations)

    override fun findByOrderId(orderId: Long): List<StockReservation> =
        stockReservationJpaRepository.findAllByOrderId(orderId)

    override fun sumActiveQuantityByProductId(productId: Long): Int =
        stockReservationJpaRepository
            .sumQuantityByProductIdAndStatus(productId, StockReservationStatus.IN_PROGRESS)
            .toInt()

    override fun sumActiveQuantityByProductIdForUpdate(productId: Long): Int =
        stockReservationJpaRepository
            .findAllByProductIdAndStatusForUpdate(productId, StockReservationStatus.IN_PROGRESS)
            .sumOf { it.quantity }

    override fun cancelActiveByOrderId(orderId: Long): Int =
        stockReservationJpaRepository.cancelActiveByOrderId(orderId)

    override fun confirmActiveByOrderId(orderId: Long): Int =
        stockReservationJpaRepository.confirmActiveByOrderId(orderId)

    override fun getActiveReservedQuantity(productId: Long): Int = sumActiveQuantityByProductId(productId)
}
