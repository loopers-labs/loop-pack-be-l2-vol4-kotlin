package com.loopers.application.queue

import com.loopers.domain.queue.WaitingQueueService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class WaitingQueueActivator(
    private val waitingQueueService: WaitingQueueService
) {
    @Scheduled(fixedDelayString = "\${queue.activate.interval}")
    fun activate() {
        waitingQueueService.activateNext(BATCH_SIZE)
    }

    companion object {
        private const val BATCH_SIZE = 100
    }
}
