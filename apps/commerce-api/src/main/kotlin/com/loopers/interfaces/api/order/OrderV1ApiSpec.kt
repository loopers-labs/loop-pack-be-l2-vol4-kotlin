package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Order", description = "주문 API")
interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "재고 차감 + 스냅샷 저장 (PENDING 상태)")
    fun createOrder(
        loginId: String,
        password: String,
        request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse>

    @Operation(summary = "내 주문 목록 조회", description = "기간 필터(startAt/endAt) 지원")
    fun getMyOrders(
        loginId: String,
        password: String,
        startAt: String?,
        endAt: String?,
        page: Int,
        size: Int,
    ): ApiResponse<*>

    @Operation(summary = "주문 상세 조회", description = "OrderItem 포함")
    fun getOrderDetail(
        loginId: String,
        password: String,
        orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse>
}
