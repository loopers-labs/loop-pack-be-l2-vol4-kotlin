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
        val mergedItems = items.groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
            .toSortedMap()
        val stocks = catalogStockPort.lockStocks(mergedItems.keys)
            .associateBy { it.productId }
        if (stocks.keys != mergedItems.keys) {
            throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")
        }

        mergedItems.forEach { (productId, quantity) ->
            val activeReservedQuantity = stockReservationRepository.sumActiveQuantityByProductIdForUpdate(productId)
            val availableQuantity = stocks.getValue(productId).stockQuantity - activeReservedQuantity
            if (availableQuantity < quantity) {
                throw CoreException(ErrorType.CONFLICT, "재고가 부족합니다.")
            }
        }

        stockReservationRepository.saveAll(
            mergedItems.map { (productId, quantity) ->
                StockReservation(orderId = orderId, productId = productId, quantity = quantity)
            },
        )
    }

    @Transactional
    fun confirmAndDeduct(orderId: Long) {
        val reservations = stockReservationRepository.findByOrderId(orderId)
            .filter { it.status == StockReservationStatus.ACTIVE }
        if (reservations.isEmpty()) throw CoreException(ErrorType.CONFLICT, "확정할 활성 예약이 없습니다.")

        val quantitiesByProductId = reservations
            .groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
            .toSortedMap()
        catalogStockPort.deductAll(quantitiesByProductId)
        val updatedCount = stockReservationRepository.confirmActiveByOrderId(orderId)
        if (updatedCount != reservations.size) {
            throw CoreException(ErrorType.CONFLICT, "예약 상태가 변경되어 요청을 처리할 수 없습니다.")
        }
    }

    @Transactional
    fun cancelActive(orderId: Long, expectedCount: Int) {
        val updatedCount = stockReservationRepository.cancelActiveByOrderId(orderId)
        if (updatedCount != expectedCount) {
            throw CoreException(ErrorType.CONFLICT, "예약 상태가 변경되어 요청을 처리할 수 없습니다.")
        }
    }

    @Transactional(readOnly = true)
    fun countActive(orderId: Long): Int =
        stockReservationRepository.findByOrderId(orderId)
            .count { it.status == StockReservationStatus.ACTIVE }

    @Transactional
    fun restoreConfirmed(orderId: Long) {
        val quantitiesByProductId = stockReservationRepository.findByOrderId(orderId)
            .filter { it.status == StockReservationStatus.CONFIRMED }
            .groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
            .toSortedMap()
        if (quantitiesByProductId.isEmpty()) throw CoreException(ErrorType.CONFLICT, "복구할 확정 예약이 없습니다.")

        catalogStockPort.restoreAll(quantitiesByProductId)
    }
}
