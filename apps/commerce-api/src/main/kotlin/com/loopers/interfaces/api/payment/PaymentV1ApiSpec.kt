package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

@Tag(name = "Payment V1", description = "결제 API")
interface PaymentV1ApiSpec {
    @Operation(summary = "주문 결제 요청 (PG 카드 결제)")
    fun pay(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestBody request: PaymentV1Dto.PayRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>

    @Operation(summary = "PG 결제 결과 콜백 수신")
    fun callback(
        @RequestBody request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Any>

    @Operation(summary = "결제 상태 동기화 (콜백 유실/타임아웃 복구)")
    fun sync(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        orderId: Long,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>
}
