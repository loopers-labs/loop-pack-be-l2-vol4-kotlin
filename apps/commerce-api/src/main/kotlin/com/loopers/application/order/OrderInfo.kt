package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderStatus
import java.time.LocalDateTime

class OrderInfo {
    data class Detail(
        val orderId: Long,
        val userId: Long,
        val status: OrderStatus,
        val reservationExpiresAt: LocalDateTime,
        val paymentTransactionId: String?,
        val cancelReason: OrderCancelReason?,
        val deliveryAddress: String,
        val deliveryRequest: String,
        val phoneNumber: String,
        val items: List<Item>,
    ) {
        companion object {
            fun from(order: Order, items: List<OrderItem>) = Detail(
                orderId = order.id,
                userId = order.userId,
                status = order.status,
                reservationExpiresAt = order.reservationExpiresAt,
                paymentTransactionId = null,
                cancelReason = order.cancelReason,
                deliveryAddress = order.deliveryAddress,
                deliveryRequest = order.deliveryRequest,
                phoneNumber = order.phoneNumber,
                items = items.map(Item::from),
            )
        }
    }

    data class Item(
        val productId: Long,
        val productNameSnapshot: String,
        val brandNameSnapshot: String,
        val priceSnapshot: Long,
        val quantity: Int,
    ) {
        companion object {
            fun from(item: OrderItem) = Item(
                productId = item.productId,
                productNameSnapshot = item.productNameSnapshot,
                brandNameSnapshot = item.brandNameSnapshot,
                priceSnapshot = item.priceSnapshot,
                quantity = item.quantity,
            )
        }
    }
}
