package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderApplicationService
import com.loopers.application.order.OrderCheckoutFacade
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.CurrentUser
import com.loopers.support.auth.LoginRequired
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@LoginRequired
@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderCheckoutFacade: OrderCheckoutFacade,
    private val orderApplicationService: OrderApplicationService,
) : OrderV1ApiSpec {
    @GetMapping("/{orderId}")
    override fun getOrder(
        @CurrentUser user: User,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val detail = requireOwnedOrder(user, orderId)
        return OrderV1Dto.OrderResponse.from(detail)
            .let(ApiResponse.Companion::success)
    }

    @PostMapping("/checkout")
    override fun checkout(
        @CurrentUser user: User,
        @RequestBody @Valid request: OrderV1Dto.CheckoutRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> =
        orderCheckoutFacade.checkout(request.toCommand(user.id))
            .let(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)

    @PostMapping("/{orderId}/payment")
    override fun pay(
        @CurrentUser user: User,
        @PathVariable orderId: Long,
        @RequestBody @Valid request: OrderV1Dto.PayRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        requireOwnedOrder(user, orderId)
        return orderCheckoutFacade.pay(request.toCommand(orderId))
            .let(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)
    }

    @PostMapping("/{orderId}/cancel")
    override fun cancel(
        @CurrentUser user: User,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        requireOwnedOrder(user, orderId)
        return orderCheckoutFacade.cancel(OrderCommand.Cancel(orderId))
            .let(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)
    }

    private fun requireOwnedOrder(user: User, orderId: Long) =
        orderApplicationService.getDetail(orderId).also { detail ->
            if (detail.userId != user.id) {
                throw CoreException(ErrorType.FORBIDDEN, "다른 사용자의 주문은 처리할 수 없습니다.")
            }
        }
}
