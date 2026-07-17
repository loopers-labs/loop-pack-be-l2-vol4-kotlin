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
        val userId = entryTokenGate.claim(loginId, password, entryToken) // 검증=소비 원자화(중복 요청 1개만 통과)
        val response = try {
            createOrderUsecase.execute(request.toCommand(loginId = loginId, password = password))
                .let { OrderV1Dto.OrderResponse.from(it) }
        } catch (e: Exception) {
            entryToken?.let { entryTokenGate.restore(userId, it) } // 주문 실패 → 토큰 복원(재시도 보장)
            throw e
        }
        entryTokenGate.complete(userId) // 주문 완료 → capacity 회복
        return ApiResponse.success(response)
    }
}
