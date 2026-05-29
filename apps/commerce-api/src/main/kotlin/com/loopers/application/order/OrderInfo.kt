package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class OrderInfo(
    val id: Long,
    val userId: Long,
    val status: OrderStatus,
    val totalPrice: Long,
    val items: List<OrderItemInfo>,
) {
    companion object {
        fun from(order: Order): OrderInfo {
            return OrderInfo(
                id = order.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "주문 ID가 존재하지 않습니다."),
                userId = order.userId,
                status = order.status,
                totalPrice = order.totalPrice.amount,
                items = order.items.map { OrderItemInfo.from(it) },
            )
        }
    }
}

data class OrderItemInfo(
    val productId: Long,
    val productName: String,
    val productPrice: Long,
    val quantity: Int,
    val totalPrice: Long,
) {
    companion object {
        fun from(orderItem: OrderItem): OrderItemInfo {
            return OrderItemInfo(
                productId = orderItem.productId,
                productName = orderItem.productName,
                productPrice = orderItem.productPrice.amount,
                quantity = orderItem.quantity.value,
                totalPrice = orderItem.totalPrice.amount,
            )
        }
    }
}
