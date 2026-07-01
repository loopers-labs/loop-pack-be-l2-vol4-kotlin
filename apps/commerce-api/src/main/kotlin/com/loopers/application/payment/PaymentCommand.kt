package com.loopers.application.payment

import com.loopers.domain.payment.PaymentStatus

data class PayCommand(
    val userId: Long,
    val orderId: Long,
    val cardType: String,
    val cardNo: String,
)

data class PaymentCallbackCommand(
    val transactionKey: String,
    val status: PaymentStatus,
    val reason: String?,
)
