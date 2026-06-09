package com.loopers.application.order.dto

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderStatus
import java.time.ZonedDateTime

data class OrderInfo(
    val orderId: Long,
    val orderNumber: String,
    val memberId: Long,
    val status: OrderStatus,
    val totalAmount: Long,
    val orderedAt: ZonedDateTime,
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val productName: String,
        val brandName: String,
        val unitPrice: Long,
        val quantity: Long,
        val totalAmount: Long,
    )

    companion object {
        fun from(order: Order): OrderInfo {
            return OrderInfo(
                orderId = order.id,
                orderNumber = order.orderNumber,
                memberId = order.memberId,
                status = order.status,
                totalAmount = order.totalAmount,
                orderedAt = order.orderedAt,
                items = order.items.map { item ->
                    Item(
                        productId = item.productId,
                        productName = item.productName,
                        brandName = item.brandName,
                        unitPrice = item.unitPrice,
                        quantity = item.quantity,
                        totalAmount = item.totalAmount,
                    )
                },
            )
        }
    }
}
