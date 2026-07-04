package com.loopers.domain.order.presentation

import com.loopers.domain.order.presentation.response.OrderResponse
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Min

@Tag(name = "Order Admin API", description = "Loopers 주문 관리자 API 입니다.")
interface OrderAdminApiSpec {
    @Operation(
        summary = "주문 목록 조회",
        description = "관리자가 주문 목록을 조회합니다.",
    )
    fun findAdminOrders(
        ldap: String?,
        @Min(0)
        page: Int?,
        @Min(1)
        size: Int?,
    ): ApiResponse<List<OrderResponse>>

    @Operation(
        summary = "주문 상세 조회",
        description = "관리자가 주문 상세를 조회합니다.",
    )
    fun findAdminOrder(
        ldap: String?,
        orderId: Long,
    ): ApiResponse<OrderResponse>
}
