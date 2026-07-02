package com.loopers.domain.order.presentation.request

import com.loopers.domain.order.application.command.OrderItemCreateCommand
import jakarta.validation.constraints.Positive

data class OrderItemCreateRequest(
    @field:Positive
    val productId: Long,
    @field:Positive
    val quantity: Long,
) {
    fun toCommand(): OrderItemCreateCommand = OrderItemCreateCommand(
        productId = productId,
        quantity = quantity,
    )
}
