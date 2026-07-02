package com.loopers.domain.order.model

import com.loopers.domain.order.constant.OrderErrorMessages
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
    val linePrice: Money = snapshotUnitPrice * quantity

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
                throw InvalidOrderException(OrderErrorMessages.ORDER_ID_NEGATIVE)
            }
        }

        private fun validateProductId(productId: Long) {
            if (productId <= 0) {
                throw InvalidOrderException(OrderErrorMessages.PRODUCT_ID_MUST_BE_POSITIVE)
            }
        }

        private fun validateSnapshotProductName(snapshotProductName: String) {
            if (snapshotProductName.isBlank()) {
                throw InvalidOrderException(OrderErrorMessages.SNAPSHOT_PRODUCT_NAME_REQUIRED)
            }
        }
    }
}
