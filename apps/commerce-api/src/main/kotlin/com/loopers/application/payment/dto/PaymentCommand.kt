package com.loopers.application.payment.dto

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PgTransactionStatus

object PaymentCommand {
    data class Request(
        val orderId: Long,
        val cardType: CardType,
        val cardNo: String,
        val idempotencyKey: String,
    )

    data class Callback(
        val transactionKey: String,
        val orderNumber: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val status: PgTransactionStatus,
        val reason: String?,
    )
}
