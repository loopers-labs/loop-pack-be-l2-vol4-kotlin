package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderDetailInfo
import com.loopers.application.order.OrderInfo
import com.loopers.application.order.OrderItemCommand
import com.loopers.domain.order.OrderStatus
import java.time.ZonedDateTime

class OrderV1Dto {

    data class CreateOrderRequest(
        val items: List<OrderItemRequest>,
        val couponId: Long? = null,
    ) {
        data class OrderItemRequest(
            val productId: Long,
            val quantity: Long,
        )

        fun toCommands(): List<OrderItemCommand> = items.map {
            OrderItemCommand(productId = it.productId, quantity = it.quantity)
        }
    }

    data class OrderResponse(
        val orderId: Long,
        val status: OrderStatus,
        val totalPrice: Long,
        val itemCount: Int,
        val createdAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: OrderInfo) = OrderResponse(
                orderId = info.orderId,
                status = info.status,
                totalPrice = info.totalPrice,
                itemCount = info.itemCount,
                createdAt = info.createdAt,
            )
        }
    }

    data class OrderDetailResponse(
        val orderId: Long,
        val status: OrderStatus,
        val originalPrice: Long,
        val discountAmount: Long,
        val totalPrice: Long,
        val couponId: Long?,
        val createdAt: ZonedDateTime,
        val items: List<OrderItemResponse>,
    ) {
        data class OrderItemResponse(
            val productId: Long,
            val productName: String,
            val productPrice: Long,
            val brandName: String,
            val quantity: Long,
        )

        companion object {
            fun from(info: OrderDetailInfo) = OrderDetailResponse(
                orderId = info.orderId,
                status = info.status,
                originalPrice = info.originalPrice,
                discountAmount = info.discountAmount,
                totalPrice = info.totalPrice,
                couponId = info.couponId,
                createdAt = info.createdAt,
                items = info.items.map {
                    OrderItemResponse(
                        productId = it.productId,
                        productName = it.productName,
                        productPrice = it.productPrice,
                        brandName = it.brandName,
                        quantity = it.quantity,
                    )
                },
            )
        }
    }
}
