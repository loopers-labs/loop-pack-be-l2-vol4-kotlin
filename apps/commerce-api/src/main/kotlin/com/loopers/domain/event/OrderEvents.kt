package com.loopers.domain.event

data class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val totalPrice: Long,
    val items: List<OrderItemSnapshot>,
)

data class OrderCompletedEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<OrderItemSnapshot>,
)

data class OrderCancelledEvent(
    val orderId: Long,
    val userId: Long,
    val reason: String?,
)

data class OrderItemSnapshot(
    val productId: Long,
    val productName: String,
    val quantity: Long,
    val price: Long,
)
