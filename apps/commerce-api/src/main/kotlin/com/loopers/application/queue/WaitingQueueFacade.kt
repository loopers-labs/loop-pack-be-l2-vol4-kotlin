package com.loopers.application.queue

import com.loopers.domain.queue.WaitingQueueService
import org.springframework.stereotype.Component

@Component
class WaitingQueueFacade(
    private val waitingQueueService: WaitingQueueService,
) {
    fun enter(userId: Long): WaitingQueueInfo.Position {
        val position = waitingQueueService.enter(userId)
        return WaitingQueueInfo.Position.from(position)
    }

    fun getPosition(userId: Long): WaitingQueueInfo.Position {
        val position = waitingQueueService.getPosition(userId)
        return WaitingQueueInfo.Position.from(position)
    }
}
