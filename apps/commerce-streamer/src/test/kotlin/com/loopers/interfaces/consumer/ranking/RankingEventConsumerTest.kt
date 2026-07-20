package com.loopers.interfaces.consumer.ranking

import com.loopers.application.ranking.RankingEventService
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import com.loopers.event.NonRetryableEventException
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import java.time.ZonedDateTime

class RankingEventConsumerTest {
    private val occurredAt = ZonedDateTime.parse("2026-07-14T10:00:00+09:00")

    @DisplayName("Catalog batch 전체 update 성공 후 ack 한다")
    @Test
    fun updatesCatalogBatchBeforeAcknowledgment() {
        val service = mockk<RankingEventService>()
        val acknowledgment = mockk<Acknowledgment>()
        val messages = listOf(catalogMessage("event-1"), catalogMessage("event-2"))
        every { service.handle(any<CatalogEventMessage>()) } just Runs
        every { acknowledgment.acknowledge() } just Runs

        CatalogRankingEventConsumer(service, RankingRedisProperties()).receive(messages, acknowledgment)

        verifyOrder {
            service.handle(messages[0])
            service.handle(messages[1])
            acknowledgment.acknowledge()
        }
    }

    @DisplayName("Order batch의 계약 오류 index를 보존하고 ack 하지 않는다")
    @Test
    fun preservesInvalidOrderRecordIndex() {
        val service = mockk<RankingEventService>()
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)
        val messages = listOf(orderMessage("event-1"), orderMessage("event-2"), orderMessage("event-3"))
        every { service.handle(messages[0]) } just Runs
        every { service.handle(messages[1]) } throws NonRetryableEventException("invalid item")

        val exception = assertThrows<BatchListenerFailedException> {
            OrderRankingEventConsumer(service, RankingRedisProperties()).receive(messages, acknowledgment)
        }

        assertThat(exception.index).isEqualTo(1)
        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    private fun catalogMessage(eventId: String): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = eventId,
            eventType = CatalogEventType.PRODUCT_VIEWED,
            aggregateId = 10L,
            productId = 10L,
            brandId = 100L,
            memberId = 1L,
            version = 1L,
            occurredAt = occurredAt,
        )
    }

    private fun orderMessage(eventId: String): OrderEventMessage {
        return OrderEventMessage(
            eventId = eventId,
            eventType = OrderEventType.PAYMENT_SUCCEEDED,
            aggregateId = 20L,
            orderId = 20L,
            orderNumber = "order-20",
            memberId = 1L,
            paymentId = 30L,
            amount = 1_000L,
            occurredAt = occurredAt,
        )
    }
}
