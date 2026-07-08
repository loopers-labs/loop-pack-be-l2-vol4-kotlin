package com.loopers.application.queue

import com.loopers.application.user.UserService
import com.loopers.domain.queue.WaitingQueuePosition
import org.springframework.stereotype.Component

@Component
class WaitingQueueFacade(
    private val userService: UserService,
    private val waitingQueueService: WaitingQueueService,
) {
    fun enter(
        loginId: String,
        rawPassword: String,
    ): WaitingQueuePosition {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        return waitingQueueService.enter(user.id)
    }

    fun getPosition(
        loginId: String,
        rawPassword: String,
    ): WaitingQueuePosition {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        return waitingQueueService.getPosition(user.id)
    }
}
