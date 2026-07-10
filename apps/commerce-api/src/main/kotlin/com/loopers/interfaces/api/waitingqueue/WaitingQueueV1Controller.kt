package com.loopers.interfaces.api.waitingqueue

import com.loopers.application.waitingqueue.WaitingQueueApplicationService
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.CurrentUser
import com.loopers.support.auth.LoginRequired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/waiting-queue")
class WaitingQueueV1Controller(
    private val waitingQueueApplicationService: WaitingQueueApplicationService,
) : WaitingQueueV1ApiSpec {
    @LoginRequired
    @GetMapping("/poll/orders")
    override fun poll(
        @CurrentUser user: User,
        @RequestHeader(WAITING_QUEUE_TOKEN_HEADER) token: String,
    ): ApiResponse<WaitingQueueV1Dto.PollResponse> =
        waitingQueueApplicationService.poll(user.id, token)
            .let(WaitingQueueV1Dto.PollResponse::from)
            .let(ApiResponse.Companion::success)

    @GetMapping("/health/orders")
    override fun health(): ApiResponse<WaitingQueueV1Dto.HealthResponse> =
        waitingQueueApplicationService.health()
            .let(WaitingQueueV1Dto.HealthResponse::from)
            .let(ApiResponse.Companion::success)

    companion object {
        const val WAITING_QUEUE_TOKEN_HEADER = "X-Waiting-Queue-Token"
    }
}
