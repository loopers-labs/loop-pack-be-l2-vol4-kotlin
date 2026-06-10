package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Order V1 API", description = "관리자 주문 API 입니다.")
interface AdminOrderV1ApiSpec {
    @Operation(
        summary = "관리자 주문 목록 조회",
        description = "관리자가 전체 주문 목록을 조회합니다.",
    )
    fun getOrders(
        adminId: String,
        page: Int,
        size: Int,
    ): ApiResponse<PageResponse<AdminOrderV1Dto.OrderSummaryResponse>>

    @Operation(
        summary = "관리자 주문 상세 조회",
        description = "관리자가 특정 주문 상세를 조회합니다.",
    )
    fun getOrder(
        adminId: String,
        orderId: Long,
    ): ApiResponse<AdminOrderV1Dto.OrderDetailResponse>
}
