package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueueFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class QueueV1Controller(
    private val queueFacade: QueueFacade,
) : QueueV1ApiSpec {
    @PostMapping("/enter")
    override fun enter(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
    ): ApiResponse<QueueV1Dto.QueueResponse> {
        return queueFacade.enter(loginId, loginPw)
            .let { QueueV1Dto.QueueResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/position")
    override fun position(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
    ): ApiResponse<QueueV1Dto.QueueResponse> {
        return queueFacade.position(loginId, loginPw)
            .let { QueueV1Dto.QueueResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
