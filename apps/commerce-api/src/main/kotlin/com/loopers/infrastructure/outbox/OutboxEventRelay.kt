package com.loopers.infrastructure.outbox

import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class OutboxEventRelay(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    @Qualifier(KafkaConfig.OUTBOX_KAFKA_TEMPLATE)
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    fun relay() {
        val pendingEvents = outboxEventJpaRepository
            .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)

        pendingEvents.forEach { outbox ->
            if (!claim(outbox)) {
                return@forEach
            }

            try {
                val record = ProducerRecord<String, String>(
                    outbox.topic,
                    outbox.partitionKey,
                    outbox.payload,
                )
                record.headers().add("eventId", outbox.eventId.toByteArray())
                record.headers().add("eventType", outbox.eventType.toByteArray())

                kafkaTemplate.send(record).get()
                outboxEventJpaRepository.updateStatusAndPublishedAtIfCurrent(
                    id = outbox.id,
                    currentStatus = OutboxStatus.PROCESSING,
                    targetStatus = OutboxStatus.PUBLISHED,
                    publishedAt = ZonedDateTime.now(),
                )
            } catch (e: Exception) {
                outboxEventJpaRepository.updateStatusIfCurrent(
                    id = outbox.id,
                    currentStatus = OutboxStatus.PROCESSING,
                    targetStatus = OutboxStatus.PENDING,
                )
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

    private fun claim(outbox: OutboxEventJpaEntity): Boolean {
        return outboxEventJpaRepository.updateStatusIfCurrent(
            id = outbox.id,
            currentStatus = OutboxStatus.PENDING,
            targetStatus = OutboxStatus.PROCESSING,
        ) == 1
    }
}
