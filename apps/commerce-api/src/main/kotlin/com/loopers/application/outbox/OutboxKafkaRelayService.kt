package com.loopers.application.outbox

import com.loopers.domain.outbox.Outbox
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@Component
class OutboxKafkaRelayService(
    private val outboxWriter: OutboxWriter,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun publishOnCommit(event: OutboxSavedEvent) {
        publishAndMark(event.outbox)
    }

    @Scheduled(fixedDelayString = "\${outbox.relay.interval-ms:30000}")
    fun relayCreated() {
        val threshold = ZonedDateTime.now().minusSeconds(10)
        outboxWriter.findAllCreatedBefore(threshold).forEach { publishAndMark(it) }
    }

    private fun publishAndMark(outbox: Outbox) {
        runCatching {
            kafkaTemplate.send(outbox.topic, outbox.payload).get(5, TimeUnit.SECONDS)
            outboxWriter.markPublished(outbox.eventId)
        }.onFailure {
            log.error("Failed to publish outbox event eventId={}", outbox.eventId, it)
        }
    }
}
