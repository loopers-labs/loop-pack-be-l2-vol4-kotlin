package com.loopers.domain.payment

interface PgClient {
    fun requestPayment(command: PgRequestCommand): PgPaymentResult

    fun getByTransactionKey(transactionKey: String): PgPaymentResult

    fun findByOrderId(orderId: Long): PgOrderLookup
}

data class PgRequestCommand(
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val cardType: CardType,
    val cardNo: String,
    val callbackUrl: String,
)
