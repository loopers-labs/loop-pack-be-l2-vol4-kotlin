package com.loopers.application.queue

import com.loopers.domain.queue.WaitingQueueAdmissionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Profile("!test")
@Component
class WaitingQueueAdmissionScheduler(
    private val waitingQueueAdmissionService: WaitingQueueAdmissionService,
    @Value("\${queue.admission.batch-size:100}")
    private val batchSize: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${queue.admission.interval-ms:1000}")
    fun admit() {
        val admittedCount = waitingQueueAdmissionService.admit(batchSize)
        if (admittedCount > 0) {
            log.info("대기열 입장 처리: {}명에게 입장 토큰 발급", admittedCount)
        }
    }
}
