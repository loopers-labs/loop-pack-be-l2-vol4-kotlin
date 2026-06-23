package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCallbackCommand

data class PgCallbackRequest(
    val transactionKey: String,
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val status: String,
    val reason: String?,
) {
    fun toCommand(): PaymentCallbackCommand {
        return PaymentCallbackCommand(
            transactionKey = transactionKey,
            status = PgPaymentGateway.toPaymentStatus(status),
            reason = reason,
        )
    }
}
