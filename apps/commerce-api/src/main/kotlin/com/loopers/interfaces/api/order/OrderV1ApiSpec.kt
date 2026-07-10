package com.loopers.interfaces.api.order

import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

@Tag(name = "Order V1 API", description = "Loopers 주문 API 입니다.")
interface OrderV1ApiSpec {
    @Operation(summary = "주문 조회", description = "소비자의 주문 상세를 조회합니다.")
    fun getOrder(user: User, orderId: Long): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(summary = "주문창 접근", description = "주문 스냅샷과 재고 예약을 생성합니다.")
    fun checkout(
        user: User,
        @RequestHeader(name = "X-Waiting-Queue-Token", required = false) waitingQueueToken: String?,
        @RequestBody @Valid request: OrderV1Dto.CheckoutRequest,
    ): ApiResponse<*>

    /**
     * Starts a PG simulator payment transaction for the order; completion is handled later by callback.
     */
    @Operation(summary = "주문 결제", description = "PG 결제 거래를 요청하고 콜백 전까지 주문을 결제대기 상태로 유지합니다.")
    fun pay(
        user: User,
        orderId: Long,
        @RequestBody @Valid request: OrderV1Dto.PayRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(summary = "주문 취소", description = "배송 시작 전 주문을 취소합니다.")
    fun cancel(user: User, orderId: Long): ApiResponse<OrderV1Dto.OrderResponse>
}
