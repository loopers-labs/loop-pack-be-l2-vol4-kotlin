package com.loopers.domain.waitingqueue.presentation

import com.loopers.domain.user.application.info.UserInfo
import com.loopers.domain.user.presentation.auth.LoginUser
import com.loopers.domain.waitingqueue.application.WaitingQueueFacade
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/queue")
@Validated
class WaitingQueueController(
    private val waitingQueueFacade: WaitingQueueFacade,
) : WaitingQueueApiSpec {
    @PostMapping("/enter")
    override fun enter(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
    ): ApiResponse<WaitingQueueResponse> =
        waitingQueueFacade.enter(user.id)
            .let { WaitingQueueResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/position")
    override fun position(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
    ): ApiResponse<WaitingQueueResponse> =
        waitingQueueFacade.position(user.id)
            .let { WaitingQueueResponse.from(it) }
            .let { ApiResponse.success(it) }
}
