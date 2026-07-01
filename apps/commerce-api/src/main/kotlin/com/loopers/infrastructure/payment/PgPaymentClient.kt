package com.loopers.infrastructure.payment

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

@FeignClient(
    name = "pg-simulator",
    url = "\${pg-simulator.url}",
    configuration = [PgFeignConfig::class],
)
interface PgPaymentClient {
    @PostMapping("/api/v1/payments")
    fun requestPayment(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: PgPaymentRequest,
    ): PgApiResponse<PgTransactionResponse>

    @GetMapping("/api/v1/payments/{transactionKey}")
    fun getTransaction(
        @RequestHeader("X-USER-ID") userId: String,
        @PathVariable("transactionKey") transactionKey: String,
    ): PgApiResponse<PgTransactionDetailResponse>
}
