package com.loopers.application.queue

import com.loopers.domain.queue.WaitingQueueService
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class WaitingQueueActivator(
    private val waitingQueueService: WaitingQueueService,
    @Value("\${queue.activate.batch-size}")
    private val batchSize: Int,
) {
    @Scheduled(
        fixedDelayString = "\${queue.activate.interval}",
        initialDelayString = "\${queue.activate.initial-delay:0}",
    )
    fun activate() {
        waitingQueueService.activateNext(batchSize)
    }
}
