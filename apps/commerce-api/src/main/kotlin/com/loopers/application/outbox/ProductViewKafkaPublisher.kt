package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.product.ProductViewedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

// 조회는 비즈니스 쓰기가 없어 아웃박스(원자성) 대상이 아니다. best-effort 직접 발행(유실 허용).
@Component
class ProductViewKafkaPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(ProductViewKafkaPublisher::class.java)

    @Async
    @EventListener
    fun onProductViewed(event: ProductViewedEvent) {
        val payload = objectMapper.writeValueAsString(
            linkedMapOf(
                "eventId" to UUID.randomUUID().toString(),
                "type" to "PRODUCT_VIEWED",
                "productId" to event.productId,
                "occurredAt" to ZonedDateTime.now().toString(),
            ),
        )
        runCatching { kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, event.productId.toString(), payload) }
            .onFailure { log.warn("Failed to publish ProductViewedEvent. productId={}", event.productId, it) }
    }
}
