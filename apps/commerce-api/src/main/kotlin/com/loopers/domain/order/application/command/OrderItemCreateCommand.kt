package com.loopers.domain.order.application.command

data class OrderItemCreateCommand(
    val productId: Long,
    val quantity: Long,
)
