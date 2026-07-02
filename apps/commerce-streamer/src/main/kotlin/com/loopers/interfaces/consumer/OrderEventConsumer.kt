package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.kafka.EventEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * order-events 소비자 — 주문 생성 이벤트의 라인을 상품별 판매량으로 누적한다.
 * catalog-events 와 같은 규칙: 배치 처리 완료 후 수동 ack, 중복은 멱등(eventId)으로 흡수.
 */
@Component
class OrderEventConsumer(
    private val metricsEventHandler: MetricsEventHandler,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = ["\${loopers.kafka.topic.order-events}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        records.forEach { record ->
            metricsEventHandler.handle(objectMapper.readValue(record.value(), EventEnvelope::class.java))
        }
        acknowledgment.acknowledge()
    }
}
