package com.loopers.interfaces.api.waitingqueue.controller

import com.loopers.application.waitingqueue.WaitingQueueFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.waitingqueue.WaitingQueueV1ApiSpec
import com.loopers.interfaces.api.waitingqueue.dto.WaitingQueueV1Dto
import com.loopers.interfaces.support.LoopersHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class WaitingQueueV1Controller(
    private val waitingQueueFacade: WaitingQueueFacade,
) : WaitingQueueV1ApiSpec {
    @PostMapping("/enter")
    override fun enter(
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
    ): ApiResponse<WaitingQueueV1Dto.PositionResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return waitingQueueFacade.enter(loginId = loginId, rawPassword = password)
            .let(WaitingQueueV1Dto.PositionResponse::from)
            .let(ApiResponse.Companion::success)
    }

    @GetMapping("/position")
    override fun getPosition(
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
    ): ApiResponse<WaitingQueueV1Dto.PositionResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return waitingQueueFacade.getPosition(loginId = loginId, rawPassword = password)
            .let(WaitingQueueV1Dto.PositionResponse::from)
            .let(ApiResponse.Companion::success)
    }
}
