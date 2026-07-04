package com.loopers.domain.order.presentation.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.loopers.domain.order.application.command.OrderCreateCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

data class OrderCreateRequest(
    @field:Positive
    @field:JsonProperty("couponId")
    val issuedCouponId: Long? = null,
    @field:NotEmpty
    @field:Valid
    val items: List<OrderItemCreateRequest>,
) {
    fun toCommand(userId: Long, idempotencyKey: String?): OrderCreateCommand = OrderCreateCommand(
        userId = userId,
        idempotencyKey = idempotencyKey,
        issuedCouponId = issuedCouponId,
        items = items.map { it.toCommand() },
    )
}
