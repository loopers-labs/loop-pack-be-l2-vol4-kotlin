package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Payment", description = "결제 API")
interface PaymentV1ApiSpec {
    @Operation(summary = "결제 요청", description = "주문에 대한 PG 결제를 요청한다.")
    @ResponseStatus(HttpStatus.CREATED)
    fun requestPayment(
        @LoginAuth loginUser: LoginUser,
        @Valid @RequestBody request: PaymentV1Dto.RequestPaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>
}
