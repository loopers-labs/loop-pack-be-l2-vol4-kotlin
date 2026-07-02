package com.loopers.domain.payment

interface PgClient {

    fun requestPayment(userId: Long, request: PgPaymentRequest): PgPaymentResponse

    fun getTransactionStatus(userId: Long, transactionKey: String): PgTransactionDetail
}

data class PgPaymentRequest(
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

data class PgPaymentResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)

data class PgTransactionDetail(
    val transactionKey: String,
    val orderId: String,
    val amount: Long,
    val status: String,
    val reason: String?,
)
