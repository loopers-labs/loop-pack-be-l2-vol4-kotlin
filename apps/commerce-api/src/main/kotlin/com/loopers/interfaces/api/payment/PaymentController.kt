package com.loopers.interfaces.api.payment

import com.loopers.domain.auth.AuthService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentApplicationService: PaymentApplicationServicePort,
    private val authService: AuthService,
) {
    @PostMapping
    fun pay(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestBody request: PaymentV1Dto.PaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        val userId = authService.login(loginId, loginPw)
        val result = paymentApplicationService.pay(request.toCommand(userId))
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(result))
    }

    @PostMapping("/callback")
    fun handleCallback(
        @RequestBody request: PaymentV1Dto.PaymentCallbackRequest,
    ): ApiResponse<Any> {
        paymentApplicationService.handleCallback(request.toCommand())
        return ApiResponse.success()
    }
}
