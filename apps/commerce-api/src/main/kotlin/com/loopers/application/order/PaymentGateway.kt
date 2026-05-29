package com.loopers.application.order

interface PaymentGateway {
    fun approve(command: ApproveCommand): Approval

    fun cancel(paymentTransactionId: String)

    data class ApproveCommand(
        val orderId: Long,
    )

    data class Approval(
        val paymentTransactionId: String,
    )
}
