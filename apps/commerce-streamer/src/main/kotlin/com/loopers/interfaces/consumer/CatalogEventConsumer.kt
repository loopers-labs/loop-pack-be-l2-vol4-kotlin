package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * catalog-events 소비자 — 좋아요 생성/취소·상품 조회 이벤트를 상품 지표 집계로 반영한다.
 * 배치를 전부 처리한 뒤에만 수동 커밋(ack)한다 — 처리 도중 실패하면 커밋하지 않아 재전달로 복구된다(At Least Once).
 * 중복 재전달은 집계 연산의 멱등(eventId) 처리가 흡수하고, 형식이 깨진 메시지는 핸들러가 기록 후 건너뛴다.
 */
@Component
class CatalogEventConsumer(
    private val metricsEventHandler: MetricsEventHandler,
) {
    @KafkaListener(
        topics = ["\${loopers.kafka.topic.catalog-events}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        records.forEach { record ->
            metricsEventHandler.handle(record.value())
        }
        acknowledgment.acknowledge()
    }
}
