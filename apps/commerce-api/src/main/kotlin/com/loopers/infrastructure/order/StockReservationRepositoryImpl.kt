package com.loopers.infrastructure.order

import com.loopers.application.catalog.port.OrderReservationQueryPort
import com.loopers.domain.catalog.ProductStockRepository
import com.loopers.domain.order.StockReservation
import com.loopers.domain.order.StockReservationRepository
import com.loopers.domain.order.StockReservationStatus
import org.springframework.stereotype.Component

@Component
class StockReservationRepositoryImpl(
    private val stockReservationJpaRepository: StockReservationJpaRepository,
    private val productStockRepository: ProductStockRepository,
) : StockReservationRepository,
    OrderReservationQueryPort {
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

    override fun getActiveReservedQuantity(productId: Long): Int =
        productStockRepository.findByProductId(productId)?.reservedQuantity ?: 0
}
