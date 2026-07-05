package com.loopers.infrastructure.queue

import com.loopers.application.queue.usecase.PromoteQueueUsecase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@ConditionalOnProperty(name = ["queue.promoter.scheduler.enabled"], havingValue = "true", matchIfMissing = true)
@Component
class QueuePromoteScheduler(
    private val promoteQueueUsecase: PromoteQueueUsecase,
) {
    private val log = LoggerFactory.getLogger(QueuePromoteScheduler::class.java)

    @Scheduled(fixedDelay = 100)
    fun promote() {
        runCatching { promoteQueueUsecase.promoteOnce(Instant.now().toEpochMilli()) }
            .onFailure { log.warn("Queue promote tick failed", it) }
    }
}
