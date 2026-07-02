package com.loopers.infrastructure.payment

object PgFeignDto {
    data class PaymentRequest(
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String,
    )

    data class TransactionResponse(
        val transactionKey: String,
        val status: String,
        val reason: String?,
    )

    data class TransactionDetailResponse(
        val transactionKey: String,
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?,
    )

    data class OrderResponse(
        val orderId: String,
        val transactions: List<TransactionDetailResponse>,
    )
}
