package com.loopers.interfaces.api.queue

import com.loopers.domain.queue.OrderQueueService
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 주문 대기열 API.
 * 블랙 프라이데이 등 트래픽 폭증 시 주문 API 앞단에서 유저를 줄 세우고,
 * 순서대로 입장 토큰을 발급하여 시스템을 보호한다.
 */
@RestController
@RequestMapping("/api/v1/queue")
class QueueV1Controller(
    private val orderQueueService: OrderQueueService,
    private val userService: UserService,
) {

    /**
     * 대기열 진입.
     * 이미 진입한 경우 현재 순번을 반환한다.
     */
    @PostMapping("/enter")
    fun enter(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<QueueV1Dto.QueuePositionResponse> {
        val user = userService.getMe(loginId, password)
        val info = orderQueueService.enter(user.id)
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(info))
    }

    /**
     * 현재 순번 조회 (Polling).
     * 토큰이 발급되면 응답에 token이 포함된다.
     */
    @GetMapping("/position")
    fun getPosition(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<QueueV1Dto.QueuePositionResponse> {
        val user = userService.getMe(loginId, password)
        val info = orderQueueService.getPosition(user.id)
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(info))
    }
}
