package com.loopers.infrastructure.payment

/**
 * PG 시뮬레이터 연동 전용 표현. infrastructure 레이어에 가둔다.
 */
data class PgPaymentRequest(
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

data class PgApiResponse<T>(
    val meta: PgMeta?,
    val data: T?,
)

data class PgMeta(
    val result: String?,
    val errorCode: String?,
    val message: String?,
)

data class PgTransactionResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)

data class PgTransactionDetailResponse(
    val transactionKey: String,
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val status: String,
    val reason: String?,
)
