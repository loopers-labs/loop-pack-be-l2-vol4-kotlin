package com.loopers.payment.infrastructure.pg

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

interface PgPaymentClient {
    @PostMapping("/api/v1/payments")
    fun request(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: PgPaymentRequest,
    ): PgApiResponse<PgTransactionResponse>

    @GetMapping("/api/v1/payments")
    fun findByOrderId(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestParam("orderId") orderId: String,
    ): PgApiResponse<PgOrderResponse>
}

@FeignClient(name = "pg-a", url = "\${pg.clients.a.url}")
interface PgAClient : PgPaymentClient

@FeignClient(name = "pg-b", url = "\${pg.clients.b.url}")
interface PgBClient : PgPaymentClient
