package com.loopers.application.order

import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.StockReservation
import com.loopers.domain.order.StockReservationRepository
import com.loopers.domain.order.StockReservationStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StockApplicationService(
    private val catalogStockPort: CatalogStockPort,
    private val stockReservationRepository: StockReservationRepository,
) {
    @Transactional
    fun reserveAll(orderId: Long, items: List<OrderCommand.CheckoutItem>) {
        if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 품목은 비어있을 수 없습니다.")
        val mergedItems = mergeItems(items)

        catalogStockPort.reserveAll(mergedItems)
        stockReservationRepository.saveAll(
            mergedItems.map { (productId, quantity) ->
                StockReservation(orderId = orderId, productId = productId, quantity = quantity)
            },
        )
    }

    @Transactional
    fun confirmAndDeduct(orderId: Long) {
        val reservations = requireReservations(orderId, StockReservationStatus.IN_PROGRESS, "확정할 진행 중 예약이 없습니다.")
        val quantitiesByProductId = quantitiesByProductId(reservations)

        catalogStockPort.confirmReservedAll(quantitiesByProductId)
        transition(orderId, StockReservationStatus.IN_PROGRESS, StockReservationStatus.COMPLETED, reservations.size)
    }

    @Transactional
    fun expireInProgress(orderId: Long) {
        val reservations = requireReservations(orderId, StockReservationStatus.IN_PROGRESS, "만료할 진행 중 예약이 없습니다.")
        transition(orderId, StockReservationStatus.IN_PROGRESS, StockReservationStatus.EXPIRED, reservations.size)
        catalogStockPort.releaseReservedAll(quantitiesByProductId(reservations))
    }

    @Transactional
    fun cancelInProgress(orderId: Long) {
        val reservations = requireReservations(orderId, StockReservationStatus.IN_PROGRESS, "취소할 진행 중 예약이 없습니다.")
        transition(orderId, StockReservationStatus.IN_PROGRESS, StockReservationStatus.CANCELED, reservations.size)
        catalogStockPort.releaseReservedAll(quantitiesByProductId(reservations))
    }

    @Transactional
    fun cancelCompletedAndRestore(orderId: Long) {
        val reservations = requireReservations(orderId, StockReservationStatus.COMPLETED, "취소할 확정 예약이 없습니다.")
        catalogStockPort.restoreActualAll(quantitiesByProductId(reservations))
        transition(orderId, StockReservationStatus.COMPLETED, StockReservationStatus.CANCELED, reservations.size)
    }

    @Transactional(readOnly = true)
    fun findInProgress(orderId: Long): List<StockReservation> =
        stockReservationRepository.findByOrderIdAndStatus(orderId, StockReservationStatus.IN_PROGRESS)

    @Transactional
    fun cancelActive(orderId: Long, expectedCount: Int) {
        val reservations = findInProgress(orderId)
        if (reservations.size != expectedCount) {
            throw CoreException(ErrorType.CONFLICT, "예약 상태가 변경되어 요청을 처리할 수 없습니다.")
        }
        cancelInProgress(orderId)
    }

    @Transactional(readOnly = true)
    fun countActive(orderId: Long): Int = findInProgress(orderId).size

    @Transactional
    fun restoreConfirmed(orderId: Long) {
        cancelCompletedAndRestore(orderId)
    }

    private fun mergeItems(items: List<OrderCommand.CheckoutItem>): Map<Long, Int> =
        items.groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
            .toSortedMap()

    private fun requireReservations(
        orderId: Long,
        status: StockReservationStatus,
        message: String,
    ): List<StockReservation> {
        val reservations = stockReservationRepository.findByOrderIdAndStatus(orderId, status)
        if (reservations.isEmpty()) throw CoreException(ErrorType.CONFLICT, message)
        return reservations
    }

    private fun quantitiesByProductId(reservations: List<StockReservation>): Map<Long, Int> =
        reservations.groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
            .toSortedMap()

    private fun transition(
        orderId: Long,
        currentStatus: StockReservationStatus,
        nextStatus: StockReservationStatus,
        expectedCount: Int,
    ) {
        val updatedCount = stockReservationRepository.transitionByOrderId(orderId, currentStatus, nextStatus)
        if (updatedCount != expectedCount) {
            throw CoreException(ErrorType.CONFLICT, "예약 상태가 변경되어 요청을 처리할 수 없습니다.")
        }
    }
}
