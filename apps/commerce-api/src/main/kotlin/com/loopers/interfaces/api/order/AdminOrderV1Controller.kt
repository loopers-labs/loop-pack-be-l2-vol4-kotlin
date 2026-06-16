package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.support.LoopersHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/orders")
class AdminOrderV1Controller(
    private val orderFacade: OrderFacade,
) : AdminOrderV1ApiSpec {
    @GetMapping
    override fun getOrders(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<AdminOrderV1Dto.OrderSummaryResponse>> {
        LoopersHeaders.validateAdmin(adminId)

        return orderFacade.getOrdersForAdmin(page = page, size = size)
            .map(AdminOrderV1Dto.OrderSummaryResponse::from)
            .let { PageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    override fun getOrder(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable orderId: Long,
    ): ApiResponse<AdminOrderV1Dto.OrderDetailResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return orderFacade.getOrderForAdmin(orderId)
            .let(AdminOrderV1Dto.OrderDetailResponse::from)
            .let { ApiResponse.success(it) }
    }
}
