package com.loopers.interfaces.consumer

import com.loopers.application.metrics.MetricEventMapper
import com.loopers.application.metrics.ProductMetricsService
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class MetricsConsumer(
    private val mapper: MetricEventMapper,
    private val service: ProductMetricsService,
) {
    private val log = LoggerFactory.getLogger(MetricsConsumer::class.java)

    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        records.forEach { record ->
            runCatching {
                val json = String(record.value(), Charsets.UTF_8)
                val command = mapper.toCommand(json)
                if (command != null) {
                    service.applyOnce(command.eventId, command.deltas)
                } else {
                    log.warn("Unknown metric event skipped. topic={} payload={}", record.topic(), json)
                }
            }.onFailure {
                log.error(
                    "Failed to process metric record, skipping. topic={} partition={} offset={} payload={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    String(record.value(), Charsets.UTF_8),
                    it,
                )
            }
        }
        acknowledgment.acknowledge()
    }
}
