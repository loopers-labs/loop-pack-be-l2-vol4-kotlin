package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.infrastructure.payment.PgCallbackRequest
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentCallbackV1Controller(
    private val paymentFacade: PaymentFacade,
) {
    @PostMapping("/callback")
    fun handleCallback(
        @RequestBody request: PgCallbackRequest,
    ): ApiResponse<Any> {
        paymentFacade.handleCallback(request.toCommand())
        return ApiResponse.success()
    }
}
