package com.loopers.interfaces.api.order

import com.loopers.application.order.usecase.CreateOrderUsecase
import com.loopers.application.queue.EntryTokenGate
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val createOrderUsecase: CreateOrderUsecase,
    private val entryTokenGate: EntryTokenGate,
) {
    @PostMapping
    fun order(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @RequestHeader(value = "X-Entry-Token", required = false) entryToken: String?,
        @RequestBody request: OrderV1Dto.OrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val userId = entryTokenGate.validate(loginId, password, entryToken)
        val response = createOrderUsecase.execute(request.toCommand(loginId = loginId, password = password))
            .let { OrderV1Dto.OrderResponse.from(it) }
        entryTokenGate.consume(userId) // 주문 완료 후 토큰 삭제
        return ApiResponse.success(response)
    }
}
