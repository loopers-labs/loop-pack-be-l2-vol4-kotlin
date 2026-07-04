package com.loopers.application.product

import com.loopers.config.kafka.KafkaTopics
import com.loopers.domain.product.ProductEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ProductViewEventHandler(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 조회는 readOnly + 빈번 → best-effort 직접 발행 (outbox 생략).
     * .get() 을 호출하지 않아 fire-and-forget → 조회 응답 지연 없음.
     */
    @EventListener
    fun handle(event: ProductEvent.Viewed) {
        runCatching {
            kafkaTemplate.send(KafkaTopics.VIEW_EVENTS, event.productId.toString(), event)
        }.onFailure { log.warn("조회수 발행 실패 productId={}, {}", event.productId, it.message) }
    }
}
