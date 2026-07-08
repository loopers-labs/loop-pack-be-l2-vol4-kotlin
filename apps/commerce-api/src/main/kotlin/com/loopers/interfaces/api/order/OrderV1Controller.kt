package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.application.queue.WaitingQueueFacade
import com.loopers.application.user.UserApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
    private val waitingQueueFacade: WaitingQueueFacade,
    private val userApplicationService: UserApplicationService,
) : OrderV1ApiSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun placeOrder(
        @LoginAuth loginUser: LoginUser,
        @RequestHeader(value = "X-Loopers-QueueToken", required = false) queueToken: String?,
        @Valid @RequestBody request: OrderV1Dto.PlaceOrderRequest,
    ): ApiResponse<OrderV1Dto.PlaceOrderResponse> {
        val userInfo = userApplicationService.getUserInfo(
            loginId = loginUser.loginId,
            rawPassword = loginUser.rawPassword,
        )
        waitingQueueFacade.verifyEntryToken(userInfo.id, queueToken)
        val info = orderFacade.placeOrder(request.toCommand(userInfo.id))
        waitingQueueFacade.completeEntry(userInfo.id)
        return ApiResponse.success(OrderV1Dto.PlaceOrderResponse.from(info))
    }
}
