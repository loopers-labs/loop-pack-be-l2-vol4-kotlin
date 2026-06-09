package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime
import java.util.UUID

class Order(
    val id: Long = 0L,
    val orderNumber: String,
    val memberId: Long,
    val status: OrderStatus,
    val items: List<OrderItem>,
    val totalAmount: Long,
    val orderedAt: ZonedDateTime,
) {
    init {
        if (orderNumber.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order number must not be blank.")
        }
        if (memberId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Member id must be positive.")
        }
        if (items.isEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order items must not be empty.")
        }
        if (totalAmount != items.sumOf { it.totalAmount }) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order total amount is invalid.")
        }
    }

    companion object {
        fun createCompleted(
            memberId: Long,
            items: List<OrderItem>,
            orderNumber: String = UUID.randomUUID().toString(),
            orderedAt: ZonedDateTime = ZonedDateTime.now(),
        ): Order {
            return Order(
                orderNumber = orderNumber,
                memberId = memberId,
                status = OrderStatus.COMPLETED,
                items = items,
                totalAmount = items.sumOf { it.totalAmount },
                orderedAt = orderedAt,
            )
        }
    }
}
