package com.loopers.application.queue

import com.loopers.domain.queue.WaitingQueueAdmissionService
import com.loopers.domain.queue.WaitingQueueService
import org.springframework.stereotype.Component

@Component
class WaitingQueueFacade(
    private val waitingQueueService: WaitingQueueService,
    private val waitingQueueAdmissionService: WaitingQueueAdmissionService,
) {
    fun enter(userId: Long): WaitingQueueInfo.PositionView {
        val position = waitingQueueService.enter(userId)
        return WaitingQueueInfo.PositionView.from(position)
    }

    fun getPosition(userId: Long): WaitingQueueInfo.PositionView {
        val position = waitingQueueService.getPosition(userId)
        return WaitingQueueInfo.PositionView.from(position)
    }

    fun verifyEntryToken(userId: Long, token: String?) {
        waitingQueueAdmissionService.verify(userId, token)
    }

    fun completeEntry(userId: Long) {
        waitingQueueAdmissionService.completeEntry(userId)
    }
}
