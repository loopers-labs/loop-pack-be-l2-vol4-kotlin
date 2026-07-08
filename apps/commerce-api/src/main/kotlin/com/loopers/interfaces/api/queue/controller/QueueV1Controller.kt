package com.loopers.interfaces.api.queue.controller

import com.loopers.application.queue.WaitingQueueFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.queue.QueueV1ApiSpec
import com.loopers.interfaces.api.queue.dto.QueueV1Dto
import com.loopers.interfaces.support.LoopersHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class QueueV1Controller(
    private val waitingQueueFacade: WaitingQueueFacade,
) : QueueV1ApiSpec {
    @PostMapping("/enter")
    override fun enter(
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
    ): ApiResponse<QueueV1Dto.PositionResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return waitingQueueFacade.enter(loginId = loginId, rawPassword = password)
            .let(QueueV1Dto.PositionResponse::from)
            .let(ApiResponse.Companion::success)
    }

    @GetMapping("/position")
    override fun getPosition(
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
    ): ApiResponse<QueueV1Dto.PositionResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return waitingQueueFacade.getPosition(loginId = loginId, rawPassword = password)
            .let(QueueV1Dto.PositionResponse::from)
            .let(ApiResponse.Companion::success)
    }
}
