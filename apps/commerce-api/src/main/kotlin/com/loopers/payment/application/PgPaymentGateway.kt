package com.loopers.payment.application

import com.loopers.payment.domain.CardType

interface PgPaymentGateway {
    fun submit(command: PgSubmitCommand): PgSubmitResult

    fun query(command: PgQueryCommand): PgQueryResult
}

data class PgSubmitCommand(
    val userId: Long,
    val orderKey: String,
    val cardType: CardType,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

sealed interface PgSubmitResult {
    data class Accepted(val transactionKey: String) : PgSubmitResult

    data class Rejected(val reason: String) : PgSubmitResult

    data object Failed : PgSubmitResult

    data object Unknown : PgSubmitResult
}

data class PgQueryCommand(
    val userId: Long,
    val orderKey: String,
)

sealed interface PgQueryResult {
    data class Found(val transactionKey: String, val status: PaymentResultStatus) : PgQueryResult

    data object NotFound : PgQueryResult

    data object Unreachable : PgQueryResult
}
