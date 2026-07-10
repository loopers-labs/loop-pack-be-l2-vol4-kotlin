package com.loopers.batch.waitingqueue

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "waiting-queue.orders", name = ["enabled"], havingValue = "true")
class WaitingQueueSchedulingConfig

@Component
@ConditionalOnBean(WaitingQueueAdmissionWorker::class)
class WaitingQueueAdmissionScheduler(
    private val waitingQueueAdmissionWorker: WaitingQueueAdmissionWorker,
) {
    private val log = LoggerFactory.getLogger(WaitingQueueAdmissionScheduler::class.java)

    @Scheduled(fixedDelayString = "\${waiting-queue.orders.consume-delay:1s}")
    fun admit() {
        val admitted = waitingQueueAdmissionWorker.admit()
        if (admitted > 0) {
            log.info("Admitted {} users from order waiting queue.", admitted)
        }
    }
}
