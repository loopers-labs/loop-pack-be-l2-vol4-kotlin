package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.application.metrics.IncomingEvent
import com.loopers.application.ranking.RankingEventProcessor
import com.loopers.config.kafka.KafkaConfig
import com.loopers.infrastructure.ranking.RankingCacheUpdater
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RankingEventConsumer(
    private val rankingEventProcessor: RankingEventProcessor,
    private val rankingCacheUpdater: RankingCacheUpdater,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        containerFactory = KafkaConfig.BATCH_LISTENER_WITH_DLT,
        groupId = "ranking-consumer",
    )
    fun consumeBatch(records: List<ConsumerRecord<String, ByteArray>>) {
        val events = records.map { record -> parseEvent(record) }
        if (events.isEmpty()) return

        val scores = rankingEventProcessor.process(events)
        rankingCacheUpdater.updateScores(scores)
    }

    private fun parseEvent(record: ConsumerRecord<String, ByteArray>): IncomingEvent {
        val eventId = record.headers().lastHeader("eventId")
            ?.value()?.let { String(it, Charsets.UTF_8) }
            ?: throw IllegalArgumentException("eventId header is required")

        val eventType = record.headers().lastHeader("eventType")
            ?.value()?.let { String(it, Charsets.UTF_8) }
            ?: throw IllegalArgumentException("eventType header is required")

        val payload: Map<String, Any> = objectMapper.readValue(String(record.value(), Charsets.UTF_8))
        val occurredAt = payload["occurredAt"] as? String
            ?: throw IllegalArgumentException("occurredAt is required")

        return IncomingEvent(
            eventId = eventId,
            eventType = eventType,
            occurredAt = occurredAt,
            payload = payload,
        )
    }
}
