package com.loopers.interfaces.api.order

import com.loopers.application.order.CreateOrderCommand
import com.loopers.application.order.CreateOrderItemCommand
import com.loopers.domain.order.OrderDetail
import com.loopers.domain.order.OrderItemView
import com.loopers.domain.order.OrderSummary
import java.time.ZonedDateTime

class OrderV1Dto {
    data class CreateOrderRequest(
        val items: List<CreateOrderItemRequest>,
    ) {
        fun toCommand(userId: Long): CreateOrderCommand = CreateOrderCommand(
            userId = userId,
            items = items.map { CreateOrderItemCommand(productId = it.productId, quantity = it.quantity) },
        )
    }

    data class CreateOrderItemRequest(
        val productId: Long,
        val quantity: Int,
    )

    data class OrderItemResponse(
        val productId: Long,
        val quantity: Int,
        val snapshotProductName: String,
        val snapshotPrice: Long,
        val snapshotBrandName: String,
        val subtotal: Long,
    ) {
        companion object {
            fun from(view: OrderItemView): OrderItemResponse = OrderItemResponse(
                productId = view.productId,
                quantity = view.quantity,
                snapshotProductName = view.snapshotProductName,
                snapshotPrice = view.snapshotPrice,
                snapshotBrandName = view.snapshotBrandName,
                subtotal = view.subtotal,
            )
        }
    }

    data class OrderDetailResponse(
        val id: Long,
        val status: String,
        val totalAmount: Long,
        val usedPoint: Long,
        val actualAmount: Long,
        val earnPoint: Long,
        val orderedAt: ZonedDateTime,
        val items: List<OrderItemResponse>,
    ) {
        companion object {
            fun from(detail: OrderDetail): OrderDetailResponse = OrderDetailResponse(
                id = detail.id,
                status = detail.status.name,
                totalAmount = detail.totalAmount,
                usedPoint = detail.usedPoint,
                actualAmount = detail.actualAmount,
                earnPoint = detail.earnPoint,
                orderedAt = detail.orderedAt,
                items = detail.items.map { OrderItemResponse.from(it) },
            )
        }
    }

    data class OrderSummaryResponse(
        val id: Long,
        val status: String,
        val totalAmount: Long,
        val usedPoint: Long,
        val orderedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(summary: OrderSummary): OrderSummaryResponse = OrderSummaryResponse(
                id = summary.id,
                status = summary.status.name,
                totalAmount = summary.totalAmount,
                usedPoint = summary.usedPoint,
                orderedAt = summary.orderedAt,
            )
        }
    }
}
