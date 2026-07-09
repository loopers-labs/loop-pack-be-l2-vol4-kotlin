package com.loopers.application.waitingqueue

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class WaitingQueueScheduler(
    private val waitingQueueService: WaitingQueueService,
    private val properties: WaitingQueueProperties,
) {
    @Scheduled(fixedDelayString = "\${commerce.queue.scheduler.fixed-delay-millis:1000}")
    fun issueNextEntries() {
        waitingQueueService.issueNextEntries(properties.scheduler.batchSize)
    }
}
