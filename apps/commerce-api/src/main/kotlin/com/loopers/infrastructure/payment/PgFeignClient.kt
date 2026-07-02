package com.loopers.infrastructure.payment

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "pg-payment",
    url = "\${pg.base-url}",
    fallbackFactory = PgFeignClientFallbackFactory::class,
)
interface PgFeignClient {
    @PostMapping("/api/v1/payments")
    fun requestPayment(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: PgPaymentRequest,
    ): PgApiResponse<PgTransactionResponse>

    @GetMapping("/api/v1/payments/{transactionKey}")
    fun getByTransactionKey(
        @RequestHeader("X-USER-ID") userId: String,
        @PathVariable("transactionKey") transactionKey: String,
    ): PgApiResponse<PgTransactionResponse>

    @GetMapping("/api/v1/payments")
    fun findByOrderId(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestParam("orderId") orderId: String,
    ): PgApiResponse<PgOrderTransactionsResponse>
}
