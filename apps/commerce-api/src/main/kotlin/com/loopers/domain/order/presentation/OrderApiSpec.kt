package com.loopers.domain.order.presentation

import com.loopers.domain.order.presentation.request.OrderCreateRequest
import com.loopers.domain.order.presentation.response.OrderResponse
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.time.LocalDate

@Tag(name = "Order API", description = "Loopers 주문 API 입니다.")
interface OrderApiSpec {
    @Operation(
        summary = "주문 요청",
        description = "로그인 사용자가 주문을 생성합니다. issuedCouponId가 있으면 쿠폰을 함께 사용 처리합니다.",
    )
    fun placeOrder(
        user: UserInfo,
        idempotencyKey: String?,
        queueToken: String?,
        @Valid request: OrderCreateRequest,
    ): ApiResponse<OrderResponse>

    @Operation(
        summary = "내 주문 목록 조회",
        description = "로그인 사용자의 주문 목록을 기간 조건으로 조회합니다.",
    )
    fun findMyOrders(
        user: UserInfo,
        startAt: LocalDate?,
        endAt: LocalDate?,
    ): ApiResponse<List<OrderResponse>>

    @Operation(
        summary = "내 주문 상세 조회",
        description = "로그인 사용자의 주문 상세를 조회합니다.",
    )
    fun findMyOrder(
        user: UserInfo,
        orderId: Long,
    ): ApiResponse<OrderResponse>
}
