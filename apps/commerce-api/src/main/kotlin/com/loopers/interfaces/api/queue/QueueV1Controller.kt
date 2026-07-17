package com.loopers.interfaces.api.queue

import com.loopers.application.queue.usecase.EnterQueueUsecase
import com.loopers.application.queue.usecase.GetQueuePositionUsecase
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class QueueV1Controller(
    private val enterQueueUsecase: EnterQueueUsecase,
    private val getQueuePositionUsecase: GetQueuePositionUsecase,
) {
    @PostMapping("/enter")
    fun enter(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<QueueV1Dto.EnterResponse> =
        enterQueueUsecase.execute(loginId, password)
            .let { ApiResponse.success(QueueV1Dto.EnterResponse(position = it)) }

    @GetMapping("/position")
    fun position(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<QueueV1Dto.PositionResponse> =
        getQueuePositionUsecase.execute(loginId, password)
            .let { ApiResponse.success(QueueV1Dto.PositionResponse.from(it)) }
}
