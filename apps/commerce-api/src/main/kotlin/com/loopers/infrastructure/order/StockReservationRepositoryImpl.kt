package com.loopers.infrastructure.order

import com.loopers.domain.order.StockReservation
import com.loopers.domain.order.StockReservationRepository
import com.loopers.domain.order.StockReservationStatus
import org.springframework.stereotype.Component

@Component
class StockReservationRepositoryImpl(
    private val stockReservationJpaRepository: StockReservationJpaRepository,
) : StockReservationRepository {
    override fun saveAll(reservations: List<StockReservation>): List<StockReservation> =
        stockReservationJpaRepository.saveAll(reservations)

    override fun findByOrderId(orderId: Long): List<StockReservation> =
        stockReservationJpaRepository.findAllByOrderId(orderId)

    override fun findByOrderIdAndStatus(orderId: Long, status: StockReservationStatus): List<StockReservation> =
        stockReservationJpaRepository.findAllByOrderIdAndStatusAndDeletedAtIsNull(orderId, status)

    override fun transitionByOrderId(
        orderId: Long,
        currentStatus: StockReservationStatus,
        nextStatus: StockReservationStatus,
    ): Int =
        stockReservationJpaRepository.transitionByOrderId(orderId, currentStatus, nextStatus)
}
