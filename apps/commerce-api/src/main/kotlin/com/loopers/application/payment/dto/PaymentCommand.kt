package com.loopers.application.payment.dto

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PgTransactionStatus

object PaymentCommand {
    data class Request(
        val orderId: Long,
        val cardType: CardType,
        val cardNo: String,
    )

    data class Callback(
        val transactionKey: String,
        val orderId: Long,
        val amount: Long,
        val status: PgTransactionStatus,
        val reason: String?,
    )
}
