package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.ApiResponse
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
    private val userService: UserService,
) : PaymentV1ApiSpec {

    @PostMapping
    override fun requestPayment(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @RequestBody request: PaymentV1Dto.PaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        val user = userService.getMe(loginId, password)
        return paymentFacade.requestPayment(
            userId = user.id,
            orderId = request.orderId,
            cardType = request.cardType,
            cardNo = request.cardNo,
        ).let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("/callback")
    override fun handleCallback(
        @RequestBody request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Void> {
        paymentFacade.handleCallback(
            transactionKey = request.transactionKey,
            status = request.status,
            reason = request.reason,
        )
        return ApiResponse.success(null)
    }

    @GetMapping("/{orderId}")
    override fun getPaymentStatus(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable orderId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        val user = userService.getMe(loginId, password)
        return paymentFacade.getPaymentStatus(user.id, orderId)
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("/{orderId}/verify")
    override fun verifyPaymentStatus(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable orderId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        val user = userService.getMe(loginId, password)
        return paymentFacade.verifyPaymentStatus(user.id, orderId)
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
