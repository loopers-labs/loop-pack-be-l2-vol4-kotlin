package com.loopers.application.event

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.domain.coupon.event.CouponIssueRequestEvent
import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.domain.like.event.ProductLikeEvent
import com.loopers.domain.order.event.OrderEvent
import com.loopers.domain.payment.event.PaymentEvent
import com.loopers.domain.product.event.ProductEvent
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import com.loopers.event.CouponIssueRequestMessage
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.ZonedDateTime

class EventRecordServiceTest {
    @DisplayName("좋아요 이벤트를 catalog outbox record 로 저장한다")
    @Test
    fun recordsLikedEvent() {
        val repository = RecordingEventOutboxRepository()
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val service = EventRecordService(
            eventOutboxRepository = repository,
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )
        val occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00")

        service.record(
            ProductLikeEvent.Like(
                memberId = 1L,
                productId = 10L,
                brandId = 100L,
                eventId = "event-1",
                occurredAt = occurredAt,
                version = 123L,
            ),
        )

        val outbox = repository.events.single()
        val payload = objectMapper.readValue<CatalogEventMessage>(outbox.payload)
        assertAll(
            { assertThat(outbox.eventId).isEqualTo("event-1") },
            { assertThat(outbox.topic).isEqualTo("catalog-events") },
            { assertThat(outbox.partitionKey).isEqualTo("10") },
            { assertThat(outbox.eventType).isEqualTo(CatalogEventType.PRODUCT_LIKED.name) },
            { assertThat(payload.eventType).isEqualTo(CatalogEventType.PRODUCT_LIKED) },
            { assertThat(payload.productId).isEqualTo(10L) },
            { assertThat(payload.memberId).isEqualTo(1L) },
            { assertThat(payload.version).isEqualTo(123L) },
        )
    }

    @DisplayName("주문 생성 이벤트를 order outbox record 로 저장한다")
    @Test
    fun recordsOrderCreatedEvent() {
        val repository = RecordingEventOutboxRepository()
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val service = EventRecordService(
            eventOutboxRepository = repository,
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )
        val occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00")

        service.record(
            OrderEvent.Created(
                orderId = 20L,
                orderNumber = "order-20",
                memberId = 1L,
                amount = 10_000L,
                eventId = "event-2",
                occurredAt = occurredAt,
            ),
        )

        val outbox = repository.events.single()
        val payload = objectMapper.readValue<OrderEventMessage>(outbox.payload)
        assertAll(
            { assertThat(outbox.eventId).isEqualTo("event-2") },
            { assertThat(outbox.topic).isEqualTo("order-events") },
            { assertThat(outbox.partitionKey).isEqualTo("20") },
            { assertThat(outbox.eventType).isEqualTo(OrderEventType.ORDER_CREATED.name) },
            { assertThat(payload.eventType).isEqualTo(OrderEventType.ORDER_CREATED) },
            { assertThat(payload.orderId).isEqualTo(20L) },
            { assertThat(payload.memberId).isEqualTo(1L) },
            { assertThat(payload.paymentId).isNull() },
        )
    }

    @DisplayName("상품 조회 이벤트를 catalog outbox record 로 저장한다")
    @Test
    fun recordsProductViewedEvent() {
        val repository = RecordingEventOutboxRepository()
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val service = EventRecordService(
            eventOutboxRepository = repository,
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )
        val occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00")

        service.record(
            ProductEvent.Viewed(
                productId = 10L,
                brandId = 100L,
                eventId = "event-4",
                occurredAt = occurredAt,
                version = 123L,
            ),
        )

        val outbox = repository.events.single()
        val payload = objectMapper.readValue<CatalogEventMessage>(outbox.payload)
        assertAll(
            { assertThat(outbox.eventId).isEqualTo("event-4") },
            { assertThat(outbox.topic).isEqualTo("catalog-events") },
            { assertThat(outbox.partitionKey).isEqualTo("10") },
            { assertThat(outbox.eventType).isEqualTo(CatalogEventType.PRODUCT_VIEWED.name) },
            { assertThat(payload.eventType).isEqualTo(CatalogEventType.PRODUCT_VIEWED) },
            { assertThat(payload.productId).isEqualTo(10L) },
            { assertThat(payload.brandId).isEqualTo(100L) },
        )
    }

    @DisplayName("결제 성공 이벤트를 order outbox record 로 저장한다")
    @Test
    fun recordsPaymentSucceededEvent() {
        val repository = RecordingEventOutboxRepository()
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val service = EventRecordService(
            eventOutboxRepository = repository,
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )
        val occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00")

        service.record(
            PaymentEvent.Succeeded(
                paymentId = 30L,
                orderId = 20L,
                orderNumber = "order-20",
                memberId = 1L,
                amount = 10_000L,
                items = listOf(PaymentEvent.Item(productId = 10L, quantity = 2L)),
                eventId = "event-3",
                occurredAt = occurredAt,
            ),
        )

        val outbox = repository.events.single()
        val payload = objectMapper.readValue<OrderEventMessage>(outbox.payload)
        assertAll(
            { assertThat(outbox.eventId).isEqualTo("event-3") },
            { assertThat(outbox.topic).isEqualTo("order-events") },
            { assertThat(outbox.partitionKey).isEqualTo("20") },
            { assertThat(outbox.eventType).isEqualTo(OrderEventType.PAYMENT_SUCCEEDED.name) },
            { assertThat(payload.eventType).isEqualTo(OrderEventType.PAYMENT_SUCCEEDED) },
            { assertThat(payload.orderId).isEqualTo(20L) },
            { assertThat(payload.paymentId).isEqualTo(30L) },
            { assertThat(payload.items.single().productId).isEqualTo(10L) },
            { assertThat(payload.items.single().quantity).isEqualTo(2L) },
        )
    }

    @DisplayName("쿠폰 발급 요청 이벤트를 coupon outbox record 로 저장한다")
    @Test
    fun recordsCouponIssueRequestedEvent() {
        val repository = RecordingEventOutboxRepository()
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val service = EventRecordService(
            eventOutboxRepository = repository,
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )
        val requestedAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00")

        service.record(
            CouponIssueRequestEvent.Requested(
                requestId = "request-1",
                couponId = 10L,
                memberId = 1L,
                eventId = "event-5",
                requestedAt = requestedAt,
            ),
        )

        val outbox = repository.events.single()
        val payload = objectMapper.readValue<CouponIssueRequestMessage>(outbox.payload)
        assertAll(
            { assertThat(outbox.eventId).isEqualTo("event-5") },
            { assertThat(outbox.topic).isEqualTo("coupon-issue-requests") },
            { assertThat(outbox.partitionKey).isEqualTo("10") },
            { assertThat(outbox.eventType).isEqualTo("COUPON_ISSUE_REQUESTED") },
            { assertThat(payload.requestId).isEqualTo("request-1") },
            { assertThat(payload.couponId).isEqualTo(10L) },
            { assertThat(payload.memberId).isEqualTo(1L) },
            { assertThat(payload.requestedAt.toInstant()).isEqualTo(requestedAt.toInstant()) },
        )
    }

    private class RecordingEventOutboxRepository : EventOutboxRepository {
        val events = mutableListOf<EventOutbox>()

        override fun save(eventOutbox: EventOutbox): EventOutbox {
            events.add(eventOutbox)
            return eventOutbox
        }

        override fun findByEventId(eventId: String): EventOutbox? {
            return events.find { it.eventId == eventId }
        }

        override fun findPending(limit: Int): List<EventOutbox> {
            return events.take(limit)
        }
    }
}
