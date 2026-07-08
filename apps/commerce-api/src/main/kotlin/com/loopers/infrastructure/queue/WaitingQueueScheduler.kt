package com.loopers.infrastructure.queue

import com.loopers.application.queue.WaitingQueueProperties
import com.loopers.application.queue.WaitingQueueService
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
