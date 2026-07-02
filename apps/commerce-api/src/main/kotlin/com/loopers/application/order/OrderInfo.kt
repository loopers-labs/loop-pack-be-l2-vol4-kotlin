package com.loopers.application.order

import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderStatus
import java.time.ZonedDateTime

data class OrderInfo(
    val orderId: Long,
    val status: OrderStatus,
    val totalPrice: Long,
    val itemCount: Int,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun from(order: OrderModel): OrderInfo {
            return OrderInfo(
                orderId = order.id,
                status = order.status,
                totalPrice = order.totalPrice,
                itemCount = order.orderItems.size,
                createdAt = order.createdAt,
            )
        }
    }
}
