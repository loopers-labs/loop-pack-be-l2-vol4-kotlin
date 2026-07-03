package com.loopers.domain.payment.infrastructure.pg.dto

data class PgOrderResponse(
    val transactions: List<PgTransactionResponse>,
)
