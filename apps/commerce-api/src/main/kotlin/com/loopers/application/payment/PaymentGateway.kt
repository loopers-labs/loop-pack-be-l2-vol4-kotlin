package com.loopers.application.payment

import com.loopers.domain.order.OrderAmount

interface PaymentGateway {
    fun pay(command: PaymentCommand): PaymentResult

    fun cancel(command: PaymentCancelCommand)
}

data class PaymentCommand(
    val orderId: Long,
    val userId: Long,
    val amount: OrderAmount,
)

data class PaymentCancelCommand(
    val orderId: Long,
    val userId: Long,
    val amount: OrderAmount,
)

enum class PaymentResult {
    SUCCESS,
    FAILED,
}
