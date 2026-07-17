package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.Topics
import com.loopers.config.kafka.event.OrderEvent
import com.loopers.domain.idempotency.EventHandledModel
import com.loopers.domain.idempotency.EventHandledRepository
import com.loopers.domain.metrics.ProductMetricsService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * order-events 토픽 Consumer.
 * ORDER_COMPLETED 이벤트를 소비하여 상품별 판매량/판매금액을 product_metrics에 반영한다.
 * event_handled 테이블을 통해 멱등 처리를 보장한다.
 */
@Component
class OrderEventConsumer(
    private val productMetricsService: ProductMetricsService,
    private val eventHandledRepository: EventHandledRepository,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * order-events 메시지를 1건씩 소비하고 manual ack 한다.
     * ORDER_COMPLETED일 때 주문 상품별 판매량을 집계한다.
     */
    @KafkaListener(
        topics = [Topics.ORDER_EVENTS],
        groupId = "streamer-order",
        containerFactory = "singleListenerContainerFactory",
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val event = objectMapper.readValue(record.value(), OrderEvent::class.java)

        if (eventHandledRepository.existsByEventId(event.eventId)) {
            log.info("[멱등] 이미 처리된 이벤트 skip (eventId={})", event.eventId)
            acknowledgment.acknowledge()
            return
        }

        when (event.eventType) {
            "ORDER_COMPLETED" -> {
                event.items.forEach { item ->
                    productMetricsService.addOrder(item.productId, item.quantity, item.price * item.quantity)
                }
            }
            "ORDER_CREATED", "ORDER_CANCELLED" -> {
                log.info("[Consumer] order 이벤트 기록 (type={}, orderId={})", event.eventType, event.orderId)
            }
            else -> log.warn("[Consumer] 알 수 없는 order 이벤트 타입: {}", event.eventType)
        }

        eventHandledRepository.save(EventHandledModel(eventId = event.eventId, topic = Topics.ORDER_EVENTS))
        acknowledgment.acknowledge()
        log.info("[Consumer] order 이벤트 처리 완료 (type={}, orderId={})", event.eventType, event.orderId)
    }
}
