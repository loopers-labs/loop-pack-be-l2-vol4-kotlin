package com.loopers.interfaces.consumer

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.ProductMetricsService
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * catalog-events / order-events 를 소비해 product_metrics 를 집계한다.
 * 관심사가 "집계" 하나이므로 두 토픽을 한 리스너로 구독하고, eventType 으로 분기한다.
 * 배치 처리 후 manual ack. 역직렬화 불가(poison) 레코드는 로그 후 스킵하고, 그 외 처리 실패는 재수신으로 재시도한다(멱등 흡수).
 */
@Component
class MetricsEventConsumer(
    private val productMetricsService: ProductMetricsService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        groupId = "\${metrics.consumer.group:commerce-streamer-metrics}",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(records: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        records.forEach { record ->
            try {
                val message = objectMapper.readValue(record.value(), EventMessage::class.java)
                productMetricsService.handle(message)
            } catch (e: JacksonException) {
                // 역직렬화 불가(poison): 재시도해도 영구 실패 → 로그 후 스킵 (파티션 정지 방지)
                log.error("역직렬화 실패 레코드 스킵: topic={}, offset={}", record.topic(), record.offset(), e)
            }
        }
        acknowledgment.acknowledge()
        log.info("메트릭 집계 처리: {}건", records.size)
    }
}
