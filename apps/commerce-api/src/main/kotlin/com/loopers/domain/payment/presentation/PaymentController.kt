package com.loopers.domain.payment.presentation

import com.loopers.domain.payment.application.PaymentFacade
import com.loopers.domain.payment.presentation.request.PaymentCallbackRequest
import com.loopers.domain.payment.presentation.request.PaymentRequest
import com.loopers.domain.payment.presentation.response.PaymentResponse
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.domain.user.presentation.auth.LoginUser
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
@Validated
class PaymentController(
    private val paymentFacade: PaymentFacade,
) {
    @PostMapping
    fun request(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @Valid @RequestBody request: PaymentRequest,
    ): ApiResponse<PaymentResponse> =
        paymentFacade.request(request.toCommand(user.id))
            .let { PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }

    @PostMapping("/callback")
    fun callback(
        @Valid @RequestBody request: PaymentCallbackRequest,
    ): ApiResponse<PaymentResponse> =
        paymentFacade.handleCallback(request.toCommand())
            .let { PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
}
