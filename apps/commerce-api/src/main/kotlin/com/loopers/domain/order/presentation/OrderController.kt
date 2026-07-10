package com.loopers.domain.order.presentation

import com.loopers.domain.order.application.OrderFacade
import com.loopers.domain.order.application.OrderQueueGateFacade
import com.loopers.domain.order.presentation.constant.OrderApiHeaders
import com.loopers.domain.order.presentation.request.OrderCreateRequest
import com.loopers.domain.order.presentation.response.OrderResponse
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.domain.user.presentation.auth.LoginUser
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/orders")
@Validated
class OrderController(
    private val orderFacade: OrderFacade,
    private val orderQueueGateFacade: OrderQueueGateFacade,
) : OrderApiSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun placeOrder(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @Parameter(hidden = true)
        @RequestHeader(name = OrderApiHeaders.IDEMPOTENCY_KEY, required = false)
        idempotencyKey: String?,
        @Parameter(hidden = true)
        @RequestHeader(name = OrderApiHeaders.QUEUE_TOKEN, required = false)
        queueToken: String?,
        @Valid @RequestBody request: OrderCreateRequest,
    ): ApiResponse<OrderResponse> {
        val order = orderQueueGateFacade.placeOrder(
            command = request.toCommand(user.id, idempotencyKey),
            queueToken = queueToken,
            queueIdempotencyKey = idempotencyKey,
        )
        val response = OrderResponse.from(order)
        return ApiResponse.success(response)
    }

    @GetMapping
    override fun findMyOrders(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @RequestParam(required = false) startAt: LocalDate?,
        @RequestParam(required = false) endAt: LocalDate?,
    ): ApiResponse<List<OrderResponse>> =
        orderFacade.findMyOrders(user.id, startAt, endAt)
            .map { OrderResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/{orderId}")
    override fun findMyOrder(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderResponse> =
        orderFacade.findMyOrder(user.id, orderId)
            .let { OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
}
