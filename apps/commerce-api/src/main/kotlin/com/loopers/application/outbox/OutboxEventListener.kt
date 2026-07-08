package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.like.ProductLikedEvent
import com.loopers.domain.like.ProductUnlikedEvent
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxMessage
import com.loopers.domain.product.ProductViewedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

/**
 * 시스템 간 전파가 필요한 도메인 이벤트를 Outbox 에 기록한다.
 *
 * plain @EventListener (동기) 이므로, 발행 트랜잭션이 살아있는 경우 같은 트랜잭션으로 커밋된다.
 * → 도메인 변경과 Outbox 기록의 원자성 보장 (Dual Write 회피).
 */
@Component
class OutboxEventListener(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) {
    // ── 라우팅: 이벤트 → (토픽, 파티션 키) ──────────────────────────
    // 파티션 키 = aggregateId. 같은 키는 같은 파티션 → aggregate 단위 순서 보장.
    @EventListener
    fun onProductLiked(event: ProductLikedEvent) =
        record(TOPIC_CATALOG, "ProductLiked", aggregateId = event.productId, payload = event)

    @EventListener
    fun onProductUnliked(event: ProductUnlikedEvent) =
        record(TOPIC_CATALOG, "ProductUnliked", aggregateId = event.productId, payload = event)

    @EventListener
    fun onProductViewed(event: ProductViewedEvent) =
        record(TOPIC_CATALOG, "ProductViewed", aggregateId = event.productId, payload = event)

    @EventListener
    fun onOrderCreated(event: OrderCreatedEvent) =
        record(TOPIC_ORDER, "OrderCreated", aggregateId = event.orderId, payload = event)

    // ── 공통 기록 로직 ────────────────────────────────────────────
    private fun record(topic: String, eventType: String, aggregateId: Long, payload: Any) {
        val eventId = UUID.randomUUID().toString()
        val message = OutboxMessage(
            eventId = eventId,
            eventType = eventType,
            aggregateId = aggregateId,
            occurredAt = ZonedDateTime.now(),
            payload = payload,
        )
        outboxEventRepository.save(
            OutboxEventModel(
                eventId = eventId,
                aggregateId = aggregateId,
                eventType = eventType,
                topic = topic,
                payload = objectMapper.writeValueAsString(message),
            ),
        )
    }

    companion object {
        const val TOPIC_CATALOG = "catalog-events"
        const val TOPIC_ORDER = "order-events"
    }
}
