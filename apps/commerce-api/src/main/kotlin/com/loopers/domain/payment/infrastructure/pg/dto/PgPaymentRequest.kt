package com.loopers.domain.payment.infrastructure.pg.dto

import com.loopers.domain.payment.port.PaymentGatewayRequest

data class PgPaymentRequest(
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
) {
    companion object {
        fun from(request: PaymentGatewayRequest): PgPaymentRequest = PgPaymentRequest(
            orderId = request.orderId.toString(),
            cardType = request.cardType,
            cardNo = request.cardNo,
            amount = request.amount,
            callbackUrl = request.callbackUrl,
        )
    }
}
