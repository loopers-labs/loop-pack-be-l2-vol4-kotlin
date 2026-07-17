package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderInfo
import com.loopers.domain.order.OrderService
import com.loopers.domain.queue.OrderQueueService
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.PageRequest
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

/**
 * 주문 API 컨트롤러.
 * 주문 생성 시 입장 토큰(X-Entry-Token) 검증을 수행한다.
 * 토큰이 없거나 유효하지 않으면 주문을 거부한다.
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
    private val orderService: OrderService,
    private val userService: UserService,
    private val orderQueueService: OrderQueueService,
) : OrderV1ApiSpec {

    /**
     * 주문 생성.
     * 입장 토큰 검증 → 주문 처리 → 토큰 삭제 순으로 동작한다.
     */
    @PostMapping
    override fun createOrder(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @RequestBody request: OrderV1Dto.CreateOrderRequest,
        @RequestHeader("X-Entry-Token", required = false) entryToken: String?,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse> {
        val user = userService.getMe(loginId, password)

        if (entryToken == null || !orderQueueService.validateToken(user.id, entryToken)) {
            throw CoreException(ErrorType.BAD_REQUEST, "유효한 입장 토큰이 필요합니다. 대기열에 진입해주세요.")
        }

        val result = orderFacade.createOrder(user.id, request.toCommands(), request.couponId)
            .let { OrderV1Dto.OrderDetailResponse.from(it) }

        orderQueueService.consumeToken(user.id)

        return ApiResponse.success(result)
    }

    /** 내 주문 목록 조회 */
    @GetMapping
    override fun getMyOrders(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @RequestParam(required = false) startAt: String?,
        @RequestParam(required = false) endAt: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<*> {
        val user = userService.getMe(loginId, password)
        val zoneId = ZoneId.of("Asia/Seoul")
        val startZdt = startAt?.let { LocalDate.parse(it).atStartOfDay(zoneId) }
        val endZdt = endAt?.let { LocalDate.parse(it).plusDays(1).atStartOfDay(zoneId).minusNanos(1) }
        val pageable = PageRequest.of(page, size)

        return orderService.getOrdersByUserId(user.id, startZdt, endZdt, pageable)
            .map { OrderInfo.from(it) }
            .map { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    /** 주문 상세 조회 */
    @GetMapping("/{orderId}")
    override fun getOrderDetail(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse> {
        val user = userService.getMe(loginId, password)
        val order = orderService.getOrderByIdAndUserId(orderId, user.id)
        return com.loopers.application.order.OrderDetailInfo.from(order)
            .let { OrderV1Dto.OrderDetailResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
