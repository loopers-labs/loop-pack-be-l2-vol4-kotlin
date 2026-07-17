package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxService
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Outbox 릴레이 스케줄러.
 * 1초 간격으로 PENDING 상태의 Outbox 이벤트를 조회하여 Kafka로 발행한다.
 * 발행 성공 시 PUBLISHED, 실패 시 FAILED로 상태를 전이한다.
 *
 * At Least Once 보장: 실패한 이벤트는 재시도 대상으로 남는다.
 */
@Component
class OutboxRelay(
    private val outboxService: OutboxService,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 1초마다 PENDING 이벤트를 Kafka로 릴레이한다.
     * KafkaTemplate.send().get()으로 동기 발행하여 성공 여부를 확인한다.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun relay() {
        val events = outboxService.findPendingEvents(100)
        if (events.isEmpty()) return

        events.forEach { outbox ->
            try {
                kafkaTemplate.send(outbox.topic, outbox.partitionKey, outbox.payload).get()
                outbox.markPublished()
            } catch (e: Exception) {
                log.error("[Outbox] Kafka 발행 실패 (eventId={}, topic={})", outbox.eventId, outbox.topic, e)
                outbox.markFailed()
            }
        }
        log.info("[Outbox] {} 건 릴레이 완료", events.size)
    }
}
