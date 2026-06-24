package com.loopers.interfaces.api.payment.controller

import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.payment.PaymentV1ApiSpec
import com.loopers.interfaces.api.payment.dto.PaymentV1Dto
import com.loopers.interfaces.support.LoopersHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentV1ApiSpec {
    @PostMapping("/callback")
    override fun handleCallback(
        @RequestBody request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        return paymentFacade.handleCallback(request.toCommand())
            .let(PaymentV1Dto.PaymentResponse::from)
            .let(ApiResponse.Companion::success)
    }

    @GetMapping("/{paymentId}")
    override fun getPayment(
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
        @PathVariable paymentId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return paymentFacade.getPayment(
            loginId = loginId,
            rawPassword = password,
            paymentId = paymentId,
        ).let(PaymentV1Dto.PaymentResponse::from)
            .let(ApiResponse.Companion::success)
    }

    @PostMapping
    override fun requestPayment(
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
        @RequestBody request: PaymentV1Dto.PaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return paymentFacade.requestPayment(
            loginId = loginId,
            rawPassword = password,
            command = request.toCommand(),
        ).let(PaymentV1Dto.PaymentResponse::from)
            .let(ApiResponse.Companion::success)
    }
}
