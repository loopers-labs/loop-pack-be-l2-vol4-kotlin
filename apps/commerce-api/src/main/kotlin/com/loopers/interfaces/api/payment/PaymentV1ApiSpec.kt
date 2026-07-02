package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment", description = "결제 API")
interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "PG 결제 요청 (비동기 처리)")
    fun requestPayment(
        loginId: String,
        password: String,
        request: PaymentV1Dto.PaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>

    @Operation(summary = "PG 콜백 수신", description = "PG 에서 결제 결과를 콜백으로 전달")
    fun handleCallback(
        request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Void>

    @Operation(summary = "결제 상태 조회", description = "주문의 결제 상태 조회")
    fun getPaymentStatus(
        loginId: String,
        password: String,
        orderId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>

    @Operation(summary = "결제 상태 검증", description = "PG 에서 직접 상태를 조회하여 동기화")
    fun verifyPaymentStatus(
        loginId: String,
        password: String,
        orderId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>
}
