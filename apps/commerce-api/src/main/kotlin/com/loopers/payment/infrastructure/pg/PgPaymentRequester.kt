package com.loopers.payment.infrastructure.pg

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.stereotype.Component

@Component
class PgPaymentRequester(
    private val pgAClient: PgAClient,
    private val pgBClient: PgBClient,
) {
    @CircuitBreaker(name = "pg-a")
    fun requestToPgA(userId: String, request: PgPaymentRequest): PgApiResponse<PgTransactionResponse> =
        pgAClient.request(userId, request)

    @CircuitBreaker(name = "pg-b")
    fun requestToPgB(userId: String, request: PgPaymentRequest): PgApiResponse<PgTransactionResponse> =
        pgBClient.request(userId, request)
}
