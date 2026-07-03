package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.application.user.UserApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
    private val userApplicationService: UserApplicationService,
) : PaymentV1ApiSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun requestPayment(
        @LoginAuth loginUser: LoginUser,
        @Valid @RequestBody request: PaymentV1Dto.RequestPaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        val userInfo = userApplicationService.getUserInfo(
            loginId = loginUser.loginId,
            rawPassword = loginUser.rawPassword,
        )
        val info = paymentFacade.requestPayment(request.toCommand(userInfo.id))
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info))
    }
}
