package com.loopers.interfaces.api.queue

import com.loopers.domain.queue.WaitingQueueService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class QueueV1Controller(
    private val waitingQueueService: WaitingQueueService,
) : QueueV1ApiSpec {

    @PostMapping
    override fun enter(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
    ): ApiResponse<QueueV1Dto.QueueStatusResponse> {
        return ApiResponse.success(QueueV1Dto.QueueStatusResponse.from(waitingQueueService.enter(loginId)))
    }

    @GetMapping("/me")
    override fun status(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
    ): ApiResponse<QueueV1Dto.QueueStatusResponse> {
        return ApiResponse.success(QueueV1Dto.QueueStatusResponse.from(waitingQueueService.status(loginId)))
    }
}
