package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
    override fun place(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestHeader("X-Entry-Token") entryToken: String,
        @RequestBody request: OrderV1Dto.PlaceOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val requestLines = request.items.map { OrderFacade.PlaceOrderLine(it.productId, it.quantity) }
        return orderFacade.place(loginId, loginPw, entryToken, requestLines, request.couponId)
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    override fun getOrder(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        return orderFacade.getMyOrder(loginId, loginPw, orderId)
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    override fun getOrders(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startAt: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endAt: LocalDate,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<OrderV1Dto.OrderPageResponse> {
        val zone = ZoneId.of("Asia/Seoul")
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = orderFacade.getMyOrders(
            loginId,
            loginPw,
            startAt.atStartOfDay(zone),
            endAt.plusDays(1).atStartOfDay(zone),
            pageable,
        )
        val response = OrderV1Dto.OrderPageResponse(
            content = result.content.map(OrderV1Dto.OrderResponse::from),
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size,
        )
        return ApiResponse.success(response)
    }
}
