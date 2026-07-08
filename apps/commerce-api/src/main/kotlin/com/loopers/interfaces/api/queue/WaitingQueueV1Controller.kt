package com.loopers.interfaces.api.queue

import com.loopers.application.queue.WaitingQueueFacade
import com.loopers.application.user.UserApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class WaitingQueueV1Controller(
    private val waitingQueueFacade: WaitingQueueFacade,
    private val userApplicationService: UserApplicationService,
) : WaitingQueueV1ApiSpec {
    @PostMapping("/enter")
    @ResponseStatus(HttpStatus.OK)
    override fun enter(
        @LoginAuth loginUser: LoginUser,
    ): ApiResponse<WaitingQueueV1Dto.EnterResponse> {
        val userInfo = userApplicationService.getUserInfo(
            loginId = loginUser.loginId,
            rawPassword = loginUser.rawPassword,
        )
        val info = waitingQueueFacade.enter(userInfo.id)
        return ApiResponse.success(WaitingQueueV1Dto.EnterResponse.from(info))
    }

    @GetMapping("/position")
    @ResponseStatus(HttpStatus.OK)
    override fun getPosition(
        @LoginAuth loginUser: LoginUser,
    ): ApiResponse<WaitingQueueV1Dto.PositionResponse> {
        val userInfo = userApplicationService.getUserInfo(
            loginId = loginUser.loginId,
            rawPassword = loginUser.rawPassword,
        )
        val info = waitingQueueFacade.getPosition(userInfo.id)
        return ApiResponse.success(WaitingQueueV1Dto.PositionResponse.from(info))
    }
}
