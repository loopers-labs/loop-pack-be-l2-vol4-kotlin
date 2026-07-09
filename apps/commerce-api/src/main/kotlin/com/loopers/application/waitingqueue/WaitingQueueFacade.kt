package com.loopers.application.waitingqueue

import com.loopers.application.user.UserService
import com.loopers.domain.waitingqueue.WaitingQueuePosition
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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

    fun validateEntryToken(
        loginId: String,
        rawPassword: String,
        entryToken: String?,
    ) {
        val token = entryToken?.takeIf(String::isNotBlank)
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "Entry token is required.")
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        if (!waitingQueueService.validateEntryToken(memberId = user.id, token = token)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "Entry token is invalid or expired.")
        }
    }

    fun deleteEntryToken(
        loginId: String,
        rawPassword: String,
    ) {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        waitingQueueService.deleteEntryToken(user.id)
    }
}
