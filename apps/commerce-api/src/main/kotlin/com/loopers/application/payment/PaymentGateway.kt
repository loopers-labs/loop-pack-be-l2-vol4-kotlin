package com.loopers.application.payment

import com.loopers.domain.order.OrderAmount

interface PaymentGateway {
    fun pay(command: PaymentCommand): PaymentResult
}

data class PaymentCommand(
    val orderId: Long,
    val userId: Long,
    val amount: OrderAmount,
)

enum class PaymentResult {
    SUCCESS,
    FAILED,
}
