package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

@Tag(name = "Order V1", description = "주문 API")
interface OrderV1ApiSpec {
    @Operation(summary = "주문 생성 (입장 토큰 검증 + 재고 차감 + PENDING 주문 생성)")
    fun place(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestHeader("X-Entry-Token") entryToken: String,
        @RequestBody request: OrderV1Dto.PlaceOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(summary = "본인 주문 단건 조회")
    fun getOrder(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(summary = "본인 주문 목록 조회 (기간)")
    fun getOrders(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startAt: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endAt: LocalDate,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<OrderV1Dto.OrderPageResponse>
}
