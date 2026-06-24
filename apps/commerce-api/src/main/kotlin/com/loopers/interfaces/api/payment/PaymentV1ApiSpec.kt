package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.payment.dto.PaymentV1Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment V1 API", description = "결제 API 입니다.")
interface PaymentV1ApiSpec {
    @Operation(
        summary = "결제 요청",
        description = "결제 대기 주문에 대해 PG 결제를 요청합니다.",
    )
    fun requestPayment(
        loginId: String,
        password: String,
        request: PaymentV1Dto.PaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>
}
