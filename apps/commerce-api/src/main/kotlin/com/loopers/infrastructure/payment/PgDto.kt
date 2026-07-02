package com.loopers.infrastructure.payment

// PG 응답 공통 래퍼 { meta, data }
data class PgApiResponse<T>(
    val meta: Meta?,
    val data: T?,
) {
    data class Meta(val result: String?, val errorCode: String?, val message: String?)
}

data class PgPaymentRequest(
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

// POST /payments, GET /payments/{tx} 의 data
data class PgTransactionResponse(
    val transactionKey: String?,
    val orderId: String?,
    val status: String?,
    val reason: String?,
)

// GET /payments?orderId= 의 data
data class PgOrderTransactionsResponse(
    val transactions: List<PgTransactionResponse>?,
)
