package com.loopers.application.order

import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderStatus
import java.time.ZonedDateTime

data class OrderDetailInfo(
    val orderId: Long,
    val status: OrderStatus,
    val originalPrice: Long,
    val discountAmount: Long,
    val totalPrice: Long,
    val couponId: Long?,
    val createdAt: ZonedDateTime,
    val items: List<OrderItemInfo>,
) {

    data class OrderItemInfo(
        val productId: Long,
        val productName: String,
        val productPrice: Long,
        val brandName: String,
        val quantity: Long,
    ) {
        companion object {
            fun from(item: OrderItemModel): OrderItemInfo {
                return OrderItemInfo(
                    productId = item.productId,
                    productName = item.productName,
                    productPrice = item.productPrice,
                    brandName = item.brandName,
                    quantity = item.quantity,
                )
            }
        }
    }

    companion object {
        fun from(order: OrderModel): OrderDetailInfo {
            return OrderDetailInfo(
                orderId = order.id,
                status = order.status,
                originalPrice = order.originalPrice,
                discountAmount = order.discountAmount,
                totalPrice = order.totalPrice,
                couponId = order.couponId,
                createdAt = order.createdAt,
                items = order.orderItems.map { OrderItemInfo.from(it) },
            )
        }
    }
}
