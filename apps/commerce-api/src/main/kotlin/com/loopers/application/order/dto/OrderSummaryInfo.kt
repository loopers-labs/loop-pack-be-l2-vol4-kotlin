package com.loopers.application.order.dto

import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.model.Order
import java.time.ZonedDateTime

data class OrderSummaryInfo(
    val orderId: Long,
    val orderNumber: String,
    val memberId: Long,
    val status: OrderStatus,
    val totalAmount: Long,
    val orderedAt: ZonedDateTime,
) {
    companion object {
        fun from(order: Order): OrderSummaryInfo {
            return OrderSummaryInfo(
                orderId = order.id,
                orderNumber = order.orderNumber,
                memberId = order.memberId,
                status = order.status,
                totalAmount = order.totalAmount,
                orderedAt = order.orderedAt,
            )
        }
    }
}
