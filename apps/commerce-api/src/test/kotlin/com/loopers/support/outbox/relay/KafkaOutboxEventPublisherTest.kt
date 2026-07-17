package com.loopers.support.outbox.relay

import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.event.CommerceOutboxAggregateType
import com.loopers.support.outbox.event.CommerceOutboxEventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.transaction.support.TransactionSynchronizationManager

class KafkaOutboxEventPublisherTest {
    private val kafkaTemplate = mockk<KafkaTemplate<Any, Any>>()
    private val properties = OutboxRelayProperties(
        relayPublishTimeout = Duration.ofSeconds(1),
    )
    private val publisher = KafkaOutboxEventPublisher(
        kafkaTemplate = kafkaTemplate,
        properties = properties,
    )

    @Test
    fun `저장된_topic과_partitionKey와_envelope는_재발행해도_그대로_유지된다`() {
        val topicSlot = mutableListOf<String>()
        val keySlot = mutableListOf<Any>()
        val messageSlot = mutableListOf<Any>()
        every {
            kafkaTemplate.send(capture(topicSlot), capture(keySlot), capture(messageSlot))
        } returns CompletableFuture.completedFuture(mockk<SendResult<Any, Any>>(relaxed = true))
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000123")
        val createdAt = ZonedDateTime.parse("2026-07-17T10:00:00+09:00[Asia/Seoul]")
        val event = OutboxEventModel(
            eventId = eventId,
            type = CommerceOutboxEventType.LIKE_COUNT_CHANGED_V1.name,
            aggregateType = CommerceOutboxAggregateType.PRODUCT.value,
            aggregateId = 123L,
            topicName = "stored-catalog-topic",
            partitionKey = "stored-product-key",
            payload = """{"productId":123,"userId":456,"delta":-1}""",
            createdAt = createdAt,
        )

        publisher.publish(event)
        publisher.publish(event)

        assertThat(topicSlot).containsExactly("stored-catalog-topic", "stored-catalog-topic")
        assertThat(keySlot).containsExactly("stored-product-key", "stored-product-key")
        assertThat(messageSlot).containsExactly(
            OutboxEventKafkaMessage(
                eventId = eventId,
                eventType = CommerceOutboxEventType.LIKE_COUNT_CHANGED_V1.name,
                aggregateType = CommerceOutboxAggregateType.PRODUCT.value,
                aggregateId = 123L,
                payload = """{"productId":123,"userId":456,"delta":-1}""",
                createdAt = createdAt.toString(),
            ),
            OutboxEventKafkaMessage(
                eventId = eventId,
                eventType = CommerceOutboxEventType.LIKE_COUNT_CHANGED_V1.name,
                aggregateType = CommerceOutboxAggregateType.PRODUCT.value,
                aggregateId = 123L,
                payload = """{"productId":123,"userId":456,"delta":-1}""",
                createdAt = createdAt.toString(),
            ),
        )
    }

    @Test
    fun `활성_DB_트랜잭션에서는_Kafka_전송을_시작하지_않는다`() {
        val event = OutboxEventModel.publishable(
            type = CommerceOutboxEventType.ORDER_PAID_V1.name,
            aggregateType = CommerceOutboxAggregateType.ORDER.value,
            aggregateId = 10L,
            topicName = "order-events",
            partitionKey = "10",
            payload = "{}",
        )
        TransactionSynchronizationManager.setActualTransactionActive(true)

        try {
            assertThatThrownBy { publisher.publish(event) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Outbox event publish must run outside an active transaction.")
            verify(exactly = 0) { kafkaTemplate.send(any<String>(), any(), any()) }
        } finally {
            TransactionSynchronizationManager.clear()
        }
    }
}
