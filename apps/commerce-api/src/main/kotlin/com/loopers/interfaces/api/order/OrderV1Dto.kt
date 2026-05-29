package com.loopers.interfaces.api.order

import com.loopers.application.order.dto.OrderCreateCommand
import com.loopers.application.order.dto.OrderInfo
import com.loopers.domain.order.OrderStatus
import java.time.ZonedDateTime

class OrderV1Dto {
    data class CreateOrderRequest(
        val items: List<Item>,
    ) {
        data class Item(
            val productId: Long,
            val quantity: Long,
        )

        fun toCommand(): OrderCreateCommand {
            return OrderCreateCommand(
                items = items.map { item ->
                    OrderCreateCommand.Item(
                        productId = item.productId,
                        quantity = item.quantity,
                    )
                },
            )
        }
    }

    data class OrderResponse(
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
            fun from(order: OrderInfo): OrderResponse {
                return OrderResponse(
                    orderId = order.orderId,
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
}
