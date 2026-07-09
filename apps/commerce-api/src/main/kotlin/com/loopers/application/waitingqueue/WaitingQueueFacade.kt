package com.loopers.application.waitingqueue

import com.loopers.application.user.UserService
import com.loopers.domain.waitingqueue.WaitingQueuePosition
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
