package com.loopers.domain.payment.infrastructure.pg.dto

import com.loopers.domain.payment.port.PaymentGatewayResult
import com.loopers.domain.payment.port.PaymentGatewayStatus

data class PgTransactionDetailResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
) {
    fun toGatewayResult(): PaymentGatewayResult = PaymentGatewayResult(
        transactionKey = transactionKey,
        status = PaymentGatewayStatus.valueOf(status),
        reason = reason,
    )
}
