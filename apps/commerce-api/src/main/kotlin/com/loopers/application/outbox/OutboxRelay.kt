package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.outbox.OutboxRepository
import com.loopers.domain.outbox.OutboxStatus
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun publish() {
        outboxRepository.findAllByStatus(OutboxStatus.NEW).forEach { outbox ->
            val event = objectMapper.readTree(outbox.payload)
            kafkaTemplate.send(outbox.topic, outbox.aggregateId, event).get()
            outbox.markSent()
        }
    }
}
