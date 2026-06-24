package com.loopers.application.payment

import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PgStatus

data class SyncPaymentResultCommand(
    val transactionKey: String?,
    val orderId: Long,
    val status: PgStatus,
    val failureReason: PaymentFailureReason?,
)
