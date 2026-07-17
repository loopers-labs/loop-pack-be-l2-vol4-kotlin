package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.Topics
import com.loopers.config.kafka.event.CatalogEvent
import com.loopers.domain.idempotency.EventHandledModel
import com.loopers.domain.idempotency.EventHandledRepository
import com.loopers.domain.metrics.ProductMetricsService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * catalog-events 토픽 Consumer.
 * 상품 조회/좋아요/좋아요취소 이벤트를 소비하여 product_metrics를 갱신한다.
 * event_handled 테이블을 통해 멱등 처리를 보장한다.
 */
@Component
class CatalogEventConsumer(
    private val productMetricsService: ProductMetricsService,
    private val eventHandledRepository: EventHandledRepository,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * catalog-events 메시지를 1건씩 소비하고 manual ack 한다.
     * 이미 처리된 eventId는 skip하여 중복 처리를 방지한다.
     */
    @KafkaListener(
        topics = [Topics.CATALOG_EVENTS],
        groupId = "streamer-catalog",
        containerFactory = "singleListenerContainerFactory",
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val event = objectMapper.readValue(record.value(), CatalogEvent::class.java)

        if (eventHandledRepository.existsByEventId(event.eventId)) {
            log.info("[멱등] 이미 처리된 이벤트 skip (eventId={})", event.eventId)
            acknowledgment.acknowledge()
            return
        }

        when (event.eventType) {
            "PRODUCT_VIEWED" -> productMetricsService.incrementView(event.productId)
            "PRODUCT_LIKED" -> productMetricsService.incrementLike(event.productId)
            "PRODUCT_UNLIKED" -> productMetricsService.decrementLike(event.productId)
            else -> log.warn("[Consumer] 알 수 없는 catalog 이벤트 타입: {}", event.eventType)
        }

        eventHandledRepository.save(EventHandledModel(eventId = event.eventId, topic = Topics.CATALOG_EVENTS))
        acknowledgment.acknowledge()
        log.info("[Consumer] catalog 이벤트 처리 완료 (type={}, productId={})", event.eventType, event.productId)
    }
}
