package com.loopers.interfaces.api.order

import com.loopers.domain.auth.AuthService
import com.loopers.domain.common.PageRequest
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.common.PageView
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderApplicationService: OrderApplicationServicePort,
    private val authService: AuthService,
) {
    @PostMapping
    fun createOrder(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestBody request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse> {
        val userId = authService.login(loginId, loginPw)
        val detail = orderApplicationService.createOrder(request.toCommand(userId))
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(detail))
    }

    @GetMapping
    fun getMyOrders(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestParam("startAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startAt: ZonedDateTime,
        @RequestParam("endAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endAt: ZonedDateTime,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageView<OrderV1Dto.OrderSummaryResponse>> {
        val userId = authService.login(loginId, loginPw)
        val result = orderApplicationService.getMyOrders(
            userId = userId,
            startAt = startAt,
            endAt = endAt,
            pageRequest = PageRequest(page = page, size = size),
        )
        return ApiResponse.success(PageView.from(result, OrderV1Dto.OrderSummaryResponse::from))
    }

    @GetMapping("/{id}")
    fun getMyOrder(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable id: Long,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse> {
        val userId = authService.login(loginId, loginPw)
        val detail = orderApplicationService.getMyOrder(userId = userId, orderId = id)
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(detail))
    }
}
