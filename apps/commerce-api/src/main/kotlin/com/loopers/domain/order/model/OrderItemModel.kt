package com.loopers.domain.order.model

import com.loopers.domain.order.exception.InvalidOrderException
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.Quantity

data class OrderItemModel(
    val orderId: Long = 0L,
    val productId: Long,
    val quantity: Quantity,
    val snapshotProductName: String,
    val snapshotUnitPrice: Money,
) {
    val linePrice: Money = Money.of(snapshotUnitPrice.value * quantity.value)

    init {
        validateOrderId(orderId)
        validateProductId(productId)
        validateSnapshotProductName(snapshotProductName)
    }

    fun withOrderId(orderId: Long): OrderItemModel = copy(orderId = orderId)

    companion object {
        fun snapshotOf(
            orderId: Long = 0L,
            productId: Long,
            quantity: Quantity,
            snapshotProductName: String,
            snapshotUnitPrice: Money,
        ): OrderItemModel = OrderItemModel(
            orderId = orderId,
            productId = productId,
            quantity = quantity,
            snapshotProductName = snapshotProductName,
            snapshotUnitPrice = snapshotUnitPrice,
        )

        private fun validateOrderId(orderId: Long) {
            if (orderId < 0) {
                throw InvalidOrderException("주문 ID는 음수일 수 없습니다.")
            }
        }

        private fun validateProductId(productId: Long) {
            if (productId <= 0) {
                throw InvalidOrderException("상품 ID는 양수여야 합니다.")
            }
        }

        private fun validateSnapshotProductName(snapshotProductName: String) {
            if (snapshotProductName.isBlank()) {
                throw InvalidOrderException("주문 상품명 스냅샷은 필수입니다.")
            }
        }
    }
}
