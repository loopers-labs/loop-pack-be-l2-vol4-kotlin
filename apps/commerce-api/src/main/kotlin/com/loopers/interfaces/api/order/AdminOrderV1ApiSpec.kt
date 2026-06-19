package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Order V1 API", description = "Loopers 관리자 주문 API 입니다.")
interface AdminOrderV1ApiSpec {
    @Operation(summary = "상품 주문 내역 조회", description = "상품에 대한 주문 내역을 조회합니다.")
    fun getProductOrders(productId: Long): ApiResponse<List<OrderV1Dto.OrderResponse>>

    @Operation(summary = "배송 시작 체크", description = "주문완료 주문을 배송시작 상태로 변경합니다.")
    fun startShipping(orderId: Long): ApiResponse<OrderV1Dto.OrderResponse>
}
