package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderApplicationService
import com.loopers.application.order.OrderCheckoutFacade
import com.loopers.domain.order.OrderCommand
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.Admin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Admin
@RestController
@RequestMapping("/api/v1/admin/orders")
class AdminOrderV1Controller(
    private val orderCheckoutFacade: OrderCheckoutFacade,
    private val orderApplicationService: OrderApplicationService,
) : AdminOrderV1ApiSpec {
    @GetMapping
    override fun getProductOrders(
        @RequestParam productId: Long,
    ): ApiResponse<List<OrderV1Dto.OrderResponse>> =
        orderApplicationService.getDetailsByProductId(productId)
            .map(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)

    @PostMapping("/{orderId}/shipping-start")
    override fun startShipping(
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> =
        orderCheckoutFacade.startShipping(OrderCommand.StartShipping(orderId))
            .let(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)
}
