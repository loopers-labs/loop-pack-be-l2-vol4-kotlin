package com.loopers.infrastructure.payment

import com.loopers.interfaces.api.ApiResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = "pgSimulatorClient", url = "\${pg.simulator.url}")
interface PgFeignClient {
    @PostMapping("/api/v1/payments")
    fun requestPayment(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: PgFeignDto.PaymentRequest,
    ): ApiResponse<PgFeignDto.TransactionResponse>

    @GetMapping("/api/v1/payments/{transactionKey}")
    fun getTransaction(
        @RequestHeader("X-USER-ID") userId: String,
        @PathVariable("transactionKey") transactionKey: String,
    ): ApiResponse<PgFeignDto.TransactionDetailResponse>

    @GetMapping("/api/v1/payments")
    fun getTransactionsByOrderId(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestParam("orderId") orderId: String,
    ): ApiResponse<PgFeignDto.OrderResponse>
}
