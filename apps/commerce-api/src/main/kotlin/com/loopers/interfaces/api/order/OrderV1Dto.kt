package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderCommand
import com.loopers.application.order.OrderInfo
import com.loopers.domain.order.OrderStatus
import java.math.BigDecimal

class OrderV1Dto {
    data class OrderRequest(
        val items: List<OrderItemRequest>,
    ) {
        fun toCommand(loginId: String, password: String): OrderCommand {
            return OrderCommand(
                loginId = loginId,
                password = password,
                items = items.map {
                    OrderCommand.OrderItemCommand(
                        productId = it.productId,
                        quantity = it.quantity,
                    )
                },
            )
        }
    }

    data class OrderItemRequest(
        val productId: Long,
        val quantity: Int,
    )

    data class OrderResponse(
        val id: Long,
        val status: OrderStatus,
        val totalPrice: BigDecimal,
        val items: List<OrderItemResponse>,
    ) {
        data class OrderItemResponse(
            val productId: Long,
            val productName: String,
            val price: BigDecimal,
            val quantity: Int,
            val subtotal: BigDecimal,
        )

        companion object {
            fun from(info: OrderInfo): OrderResponse {
                return OrderResponse(
                    id = info.id,
                    status = info.status,
                    totalPrice = info.totalPrice,
                    items = info.items.map {
                        OrderItemResponse(
                            productId = it.productId,
                            productName = it.productName,
                            price = it.price,
                            quantity = it.quantity,
                            subtotal = it.subtotal,
                        )
                    },
                )
            }
        }
    }
}
