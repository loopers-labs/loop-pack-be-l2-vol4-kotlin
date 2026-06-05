package com.loopers.domain.order.application.info

import com.loopers.domain.order.model.OrderItemModel
import com.loopers.domain.order.model.OrderModel

data class OrderInfo(
    val id: Long,
    val orderedUserId: Long,
    val totalPrice: Long,
    val discountPrice: Long,
    val paymentPrice: Long,
    val items: List<OrderItemInfo>,
) {
    companion object {
        fun from(order: OrderModel): OrderInfo = OrderInfo(
            id = order.id,
            orderedUserId = order.orderedUserId,
            totalPrice = order.totalPrice.value,
            discountPrice = order.discountPrice.value,
            paymentPrice = order.paymentPrice.value,
            items = order.items.map { OrderItemInfo.from(it) },
        )
    }
}

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
