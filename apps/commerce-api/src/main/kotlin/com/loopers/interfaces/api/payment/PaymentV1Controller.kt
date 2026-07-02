package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentV1ApiSpec {
    @PostMapping
    override fun pay(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @Valid @RequestBody request: PaymentV1Dto.PayRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        return paymentFacade.pay(loginId, loginPw, request.orderId.toLong(), request.cardType, request.cardNo)
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("/callback")
    override fun callback(
        @RequestBody request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Any> {
        paymentFacade.handleCallback(request.transactionKey, request.status, request.reason)
        return ApiResponse.success()
    }

    @PostMapping("/sync")
    override fun sync(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestParam orderId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        return paymentFacade.syncByUser(loginId, loginPw, orderId)
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
