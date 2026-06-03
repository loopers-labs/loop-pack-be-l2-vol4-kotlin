package com.loopers.application.order

data class CreateOrderCommand(
    val userId: Long,
    val items: List<CreateOrderItemCommand>,
)

data class CreateOrderItemCommand(
    val productId: Long,
    val quantity: Int,
)
