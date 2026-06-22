package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.dto.OrderV1Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.LocalDate

@Tag(name = "Order V1 API", description = "주문 API 입니다.")
interface OrderV1ApiSpec {
    @Operation(
        summary = "주문 생성",
        description = "여러 상품을 한 번에 주문하고, 성공 시 주문 항목 전체 재고를 차감합니다.",
    )
    fun placeOrder(
        loginId: String,
        password: String,
        request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(
        summary = "내 주문 목록 조회",
        description = "로그인한 회원의 기간 내 주문 목록을 조회합니다.",
    )
    fun getOrders(
        loginId: String,
        password: String,
        startAt: LocalDate,
        endAt: LocalDate,
    ): ApiResponse<List<OrderV1Dto.OrderSummaryResponse>>

    @Operation(
        summary = "내 주문 상세 조회",
        description = "로그인한 회원의 주문 상세를 주문 시점 스냅샷 기준으로 조회합니다.",
    )
    fun getOrder(
        loginId: String,
        password: String,
        orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse>
}
