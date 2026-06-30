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
        idempotencyKey: String,
        request: PaymentV1Dto.PaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>

    @Operation(
        summary = "결제 조회",
        description = "로그인한 회원의 결제 상태와 연결된 주문 상태를 조회합니다.",
    )
    fun getPayment(
        loginId: String,
        password: String,
        paymentId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>

    @Operation(
        summary = "결제 상태 동기화",
        description = "PG 주문별 결제 조회를 통해 타임아웃 등으로 확인이 필요한 결제 상태를 복구합니다.",
    )
    fun syncPayment(
        loginId: String,
        password: String,
        paymentId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>

    @Operation(
        summary = "PG 결제 콜백 수신",
        description = "PG 비동기 결제 결과를 수신해 내부 결제 상태와 주문 상태를 갱신합니다.",
    )
    fun handleCallback(
        request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>
}
