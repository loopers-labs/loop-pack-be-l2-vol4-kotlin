package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

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
}
