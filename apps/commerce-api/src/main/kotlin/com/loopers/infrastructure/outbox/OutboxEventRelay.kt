package com.loopers.infrastructure.outbox

import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxEventRelay(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun relay() {
        val pendingEvents = outboxEventJpaRepository
            .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)

        pendingEvents.forEach { outbox ->
            try {
                val record = ProducerRecord<Any, Any>(
                    outbox.topic,
                    outbox.partitionKey,
                    outbox.payload,
                )
                record.headers().add("eventId", outbox.eventId.toByteArray())
                record.headers().add("eventType", outbox.eventType.toByteArray())

                kafkaTemplate.send(record).get()
                outbox.markPublished()
            } catch (e: Exception) {
                log.error(
                    "Outbox 이벤트 발행 실패: id={}, eventId={}, topic={}",
                    outbox.id,
                    outbox.eventId,
                    outbox.topic,
                    e,
                )
            }
        }
    }
}
