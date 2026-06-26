package com.loopers.payment.infrastructure.pg

import com.loopers.payment.application.PgSubmitCommand
import com.loopers.payment.domain.CardType

data class PgPaymentRequest(
    val orderId: String,
    val cardType: CardType,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
) {
    companion object {
        fun from(command: PgSubmitCommand): PgPaymentRequest =
            PgPaymentRequest(
                orderId = command.orderKey,
                cardType = command.cardType,
                cardNo = command.cardNo,
                amount = command.amount,
                callbackUrl = command.callbackUrl,
            )
    }
}

data class PgApiResponse<T>(
    val meta: PgMeta,
    val data: T?,
)

data class PgMeta(
    val result: String,
    val errorCode: String?,
    val message: String?,
)

data class PgTransactionResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)

data class PgOrderResponse(
    val orderId: String,
    val transactions: List<PgTransactionResponse>,
)
