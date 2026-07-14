package com.loopers.domain.waitingqueue.scheduler

import com.loopers.domain.waitingqueue.application.WaitingQueueFacade
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "commerce.waiting-queue",
    name = ["scheduler-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class WaitingQueueScheduler(
    private val waitingQueueFacade: WaitingQueueFacade,
) {
    @Scheduled(
        fixedDelayString = "\${commerce.waiting-queue.scheduler-delay}",
        initialDelayString = "\${commerce.waiting-queue.scheduler-delay}",
    )
    fun admitBatch() {
        waitingQueueFacade.admitBatch()
    }
}
