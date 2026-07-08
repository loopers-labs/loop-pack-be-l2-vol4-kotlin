package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Order", description = "주문 API")
interface OrderV1ApiSpec {
    @Operation(
        summary = "주문 생성",
        description = "대기열 입장 토큰(X-Loopers-QueueToken)을 검증한 뒤, 재고 차감·쿠폰 적용 후 결제를 진행하고 주문을 생성한다. " +
            "유효한 입장 토큰이 없으면 403을 반환한다.",
    )
    @ResponseStatus(HttpStatus.CREATED)
    fun placeOrder(
        @LoginAuth loginUser: LoginUser,
        @RequestHeader(value = "X-Loopers-QueueToken", required = false) queueToken: String?,
        @Valid @RequestBody request: OrderV1Dto.PlaceOrderRequest,
    ): ApiResponse<OrderV1Dto.PlaceOrderResponse>
}
