package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentCallbackApplicationService
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Receives PG simulator callbacks and hands them to the payment application service.
 */
@RestController
@RequestMapping("/api/v1/payments/callback")
class PaymentCallbackV1Controller(
    private val paymentCallbackApplicationService: PaymentCallbackApplicationService,
) {
    /** Completes or records failure for a pending simulator payment transaction. */
    @PostMapping
    fun callback(
        @RequestBody @Valid request: PaymentCallbackV1Dto.Request,
    ): ApiResponse<Unit> {
        paymentCallbackApplicationService.handle(request.toCommand())
        return ApiResponse.success(Unit)
    }
}
