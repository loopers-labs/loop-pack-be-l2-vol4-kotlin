package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderStatus

enum class OrderStatusType {
    CREATED,
    PAYMENT_PENDING,
    PAYMENT_COMPLETED,
    SHIPPING,
    DELIVERED,
    CANCELLED,
    ;

    fun toDomain(): OrderStatus = when (this) {
        CREATED -> OrderStatus.CREATED
        PAYMENT_PENDING -> OrderStatus.PAYMENT_PENDING
        PAYMENT_COMPLETED -> OrderStatus.PAYMENT_COMPLETED
        SHIPPING -> OrderStatus.SHIPPING
        DELIVERED -> OrderStatus.DELIVERED
        CANCELLED -> OrderStatus.CANCELLED
    }

    companion object {
        fun from(domain: OrderStatus): OrderStatusType = when (domain) {
            OrderStatus.CREATED -> CREATED
            OrderStatus.PAYMENT_PENDING -> PAYMENT_PENDING
            OrderStatus.PAYMENT_COMPLETED -> PAYMENT_COMPLETED
            OrderStatus.SHIPPING -> SHIPPING
            OrderStatus.DELIVERED -> DELIVERED
            OrderStatus.CANCELLED -> CANCELLED
        }
    }
}
