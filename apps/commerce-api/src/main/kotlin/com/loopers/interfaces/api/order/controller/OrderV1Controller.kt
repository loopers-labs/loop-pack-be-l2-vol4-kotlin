package com.loopers.interfaces.api.order.controller

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.OrderV1ApiSpec
import com.loopers.interfaces.api.order.dto.OrderV1Dto
import com.loopers.interfaces.support.LoopersHeaders
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
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
        @RequestBody request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return orderFacade.placeOrder(
            loginId = loginId,
            rawPassword = password,
            command = request.toCommand(),
        ).let(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)
    }

    @GetMapping
    override fun getOrders(
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startAt: LocalDate,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endAt: LocalDate,
    ): ApiResponse<List<OrderV1Dto.OrderSummaryResponse>> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

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
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return orderFacade.getOrder(
            loginId = loginId,
            rawPassword = password,
            orderId = orderId,
        ).let(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)
    }
}
