package com.loopers.domain.order.application.info

import com.loopers.domain.order.model.OrderItemModel

data class OrderItemInfo(
    val productId: Long,
    val quantity: Long,
    val productName: String,
    val unitPrice: Long,
    val linePrice: Long,
) {
    companion object {
        fun from(item: OrderItemModel): OrderItemInfo = OrderItemInfo(
            productId = item.productId,
            quantity = item.quantity.value,
            productName = item.snapshotProductName,
            unitPrice = item.snapshotUnitPrice.value,
            linePrice = item.linePrice.value,
        )
    }
}
