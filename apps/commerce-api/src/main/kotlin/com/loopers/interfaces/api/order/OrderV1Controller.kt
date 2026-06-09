package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
) : OrderV1ApiSpec {
    @PostMapping
    override fun placeOrder(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @RequestBody request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        return orderFacade.placeOrder(
            loginId = loginId,
            rawPassword = password,
            command = request.toCommand(),
        ).let(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)
    }

    @GetMapping
    override fun getOrders(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startAt: LocalDate,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endAt: LocalDate,
    ): ApiResponse<List<OrderV1Dto.OrderSummaryResponse>> {
        val zoneId = ZoneId.systemDefault()

        return orderFacade.getOrders(
            loginId = loginId,
            rawPassword = password,
            startAt = startAt.atStartOfDay(zoneId),
            endAt = endAt.plusDays(1).atStartOfDay(zoneId).minusNanos(1),
        ).map(OrderV1Dto.OrderSummaryResponse::from)
            .let(ApiResponse.Companion::success)
    }

    @GetMapping("/{orderId}")
    override fun getOrder(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        return orderFacade.getOrder(
            loginId = loginId,
            rawPassword = password,
            orderId = orderId,
        ).let(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)
    }
}
