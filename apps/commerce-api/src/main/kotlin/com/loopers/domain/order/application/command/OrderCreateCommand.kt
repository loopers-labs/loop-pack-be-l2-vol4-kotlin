package com.loopers.domain.order.application.command

data class OrderCreateCommand(
    val userId: Long,
    val idempotencyKey: String? = null,
    val issuedCouponId: Long? = null,
    val items: List<OrderItemCreateCommand>,
)

data class OrderItemCreateCommand(
    val productId: Long,
    val quantity: Long,
)
