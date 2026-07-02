package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.application.metrics.IncomingEvent
import com.loopers.application.metrics.MetricsEventProcessor
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class MetricsEventConsumer(
    private val metricsEventProcessor: MetricsEventProcessor,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
        groupId = "metrics-consumer",
    )
    fun consume(
        messages: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        messages.forEach { record ->
            try {
                val eventId = record.headers().lastHeader("eventId")
                    ?.value()?.let { String(it) }
                    ?: return@forEach

                val eventType = record.headers().lastHeader("eventType")
                    ?.value()?.let { String(it) }
                    ?: return@forEach

                val json = String(record.value() as ByteArray, Charsets.UTF_8)
                val payload: Map<String, Any> = objectMapper.readValue(json)

                metricsEventProcessor.process(
                    IncomingEvent(
                        eventId = eventId,
                        eventType = eventType,
                        payload = payload,
                    ),
                )
            } catch (e: Exception) {
                log.error("이벤트 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e)
            }
        }
        acknowledgment.acknowledge()
    }
}
