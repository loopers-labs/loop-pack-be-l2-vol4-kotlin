package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import com.loopers.domain.outbox.KafkaTopics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OutboxMessageFactoryTest {
    private val om = ObjectMapper()
    private val factory = OutboxMessageFactory(om)

    @DisplayName("좋아요 생성 이벤트는 catalog-events, key=productId, type=LIKE_ADDED 로 매핑된다.")
    @Test
    fun mapsLikeCreated() {
        val draft = factory.from(LikeCreatedEvent(productId = 10L))!!
        assertThat(draft.topic).isEqualTo(KafkaTopics.CATALOG_EVENTS)
        assertThat(draft.partitionKey).isEqualTo("10")
        val node = om.readTree(draft.payload)
        assertThat(node["type"].asText()).isEqualTo("LIKE_ADDED")
        assertThat(node["productId"].asLong()).isEqualTo(10L)
        assertThat(node["eventId"].asText()).isNotBlank()
    }

    @DisplayName("좋아요 삭제 이벤트는 type=LIKE_REMOVED 로 매핑된다.")
    @Test
    fun mapsLikeDeleted() {
        val node = om.readTree(factory.from(LikeDeletedEvent(productId = 10L))!!.payload)
        assertThat(node["type"].asText()).isEqualTo("LIKE_REMOVED")
    }

    @DisplayName("결제성공 이벤트는 order-events, key=orderId, items 포함으로 매핑된다.")
    @Test
    fun mapsPaymentSucceeded() {
        val event = PaymentSucceededEvent(
            orderId = 1L,
            userId = 2L,
            items = listOf(PaymentSucceededEvent.Item(productId = 10L, quantity = 3)),
        )
        val draft = factory.from(event)!!
        assertThat(draft.topic).isEqualTo(KafkaTopics.ORDER_EVENTS)
        assertThat(draft.partitionKey).isEqualTo("1")
        val node = om.readTree(draft.payload)
        assertThat(node["type"].asText()).isEqualTo("PAYMENT_SUCCEEDED")
        assertThat(node["items"][0]["productId"].asLong()).isEqualTo(10L)
        assertThat(node["items"][0]["quantity"].asInt()).isEqualTo(3)
    }

    @DisplayName("아웃박스 대상이 아닌 이벤트(OrderCreated)는 null을 반환한다.")
    @Test
    fun ignoresNonOutboxEvent() {
        assertThat(factory.from(OrderCreatedEvent(orderId = 1L, userId = 2L, items = emptyList()))).isNull()
    }
}
