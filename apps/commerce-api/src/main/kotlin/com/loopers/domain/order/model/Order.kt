package com.loopers.domain.order.model

import com.loopers.domain.order.OrderStatus
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
    val originalAmount: Long = items.sumOf { it.totalAmount },
    val discountAmount: Long = 0L,
    val totalAmount: Long,
    val couponIssueId: Long? = null,
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
        if (originalAmount != items.sumOf { it.totalAmount }) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order original amount is invalid.")
        }
        if (discountAmount < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order discount amount must not be negative.")
        }
        if (totalAmount != originalAmount - discountAmount) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order total amount is invalid.")
        }
    }

    companion object {
        fun createCompleted(
            memberId: Long,
            items: List<OrderItem>,
            discountAmount: Long = 0L,
            couponIssueId: Long? = null,
            orderNumber: String = UUID.randomUUID().toString(),
            orderedAt: ZonedDateTime = ZonedDateTime.now(),
        ): Order {
            val originalAmount = items.sumOf { it.totalAmount }
            return Order(
                orderNumber = orderNumber,
                memberId = memberId,
                status = OrderStatus.COMPLETED,
                items = items,
                originalAmount = originalAmount,
                discountAmount = discountAmount,
                totalAmount = originalAmount - discountAmount,
                couponIssueId = couponIssueId,
                orderedAt = orderedAt,
            )
        }
    }
}
