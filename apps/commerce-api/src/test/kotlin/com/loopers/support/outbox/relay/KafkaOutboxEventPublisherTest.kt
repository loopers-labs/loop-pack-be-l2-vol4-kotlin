package com.loopers.support.outbox.relay

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.event.CommerceOutboxAggregateType
import com.loopers.support.outbox.event.CommerceOutboxEventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult

class KafkaOutboxEventPublisherTest {
    private val kafkaTemplate = mockk<KafkaTemplate<Any, Any>>()
    private val properties = OutboxRelayProperties(
        relayPublishTimeout = Duration.ofSeconds(1),
    )
    private val publisher = KafkaOutboxEventPublisher(
        kafkaTemplate = kafkaTemplate,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
    )

    @Test
    fun `좋아요_수_이벤트는_라우팅된_토픽과_aggregateId_키로_Kafka에_발행된다`() {
        val topicSlot = slot<String>()
        val keySlot = slot<Any>()
        val messageSlot = slot<Any>()
        every {
            kafkaTemplate.send(capture(topicSlot), capture(keySlot), capture(messageSlot))
        } returns CompletableFuture.completedFuture(mockk<SendResult<Any, Any>>(relaxed = true))
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000123")

        publisher.publish(
            likeCountEvent(
                eventId = eventId,
                payload = """{"productId":123,"userId":456,"delta":-1}""",
            ),
        )

        assertThat(topicSlot.captured).isEqualTo("catalog-events")
        assertThat(keySlot.captured).isEqualTo("123")
        assertThat(messageSlot.captured).isEqualTo(
            LikeCountChangedKafkaMessage(
                eventId = eventId,
                eventType = CommerceOutboxEventType.LIKE_COUNT_CHANGED_V1.name,
                productId = 123L,
                userId = 456L,
                delta = -1,
            ),
        )
    }

    @Test
    fun `일반_outbox_이벤트는_라우팅된_토픽과_aggregateId_키로_Kafka에_발행된다`() {
        val topicSlot = slot<String>()
        val keySlot = slot<Any>()
        val messageSlot = slot<Any>()
        every {
            kafkaTemplate.send(capture(topicSlot), capture(keySlot), capture(messageSlot))
        } returns CompletableFuture.completedFuture(mockk<SendResult<Any, Any>>(relaxed = true))
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000456")

        publisher.publish(
            OutboxEventModel(
                eventId = eventId,
                type = CommerceOutboxEventType.ORDER_PAID_V1.name,
                aggregateType = CommerceOutboxAggregateType.ORDER.value,
                aggregateId = 789L,
                payload = """{"orderId":789}""",
            ),
        )

        assertThat(topicSlot.captured).isEqualTo("order-events")
        assertThat(keySlot.captured).isEqualTo("789")
        assertThat(messageSlot.captured).isInstanceOf(OutboxEventKafkaMessage::class.java)
        assertThat((messageSlot.captured as OutboxEventKafkaMessage).createdAt).isInstanceOf(String::class.java)
        assertThat(messageSlot.captured).usingRecursiveComparison().isEqualTo(
            OutboxEventKafkaMessage(
                eventId = eventId,
                eventType = CommerceOutboxEventType.ORDER_PAID_V1.name,
                aggregateType = CommerceOutboxAggregateType.ORDER.value,
                aggregateId = 789L,
                payload = """{"orderId":789}""",
                createdAt = (messageSlot.captured as OutboxEventKafkaMessage).createdAt,
            ),
        )
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "productId|{\"userId\":456,\"delta\":1}",
            "userId|{\"productId\":123,\"delta\":1}",
            "delta|{\"productId\":123,\"userId\":456}",
        ],
        delimiter = '|',
    )
    fun `필수_payload_필드가_누락되면_Kafka에_발행하지_않고_실패한다`(
        fieldName: String,
        payload: String,
    ) {
        every {
            kafkaTemplate.send(any<String>(), any(), any())
        } returns CompletableFuture.completedFuture(mockk<SendResult<Any, Any>>(relaxed = true))

        assertThatThrownBy {
            publisher.publish(likeCountEvent(payload = payload))
        }.hasMessageContaining(fieldName)

        verify(exactly = 0) {
            kafkaTemplate.send(any<String>(), any(), any())
        }
    }

    private fun likeCountEvent(
        eventId: UUID = UUID.randomUUID(),
        payload: String,
    ): OutboxEventModel =
        OutboxEventModel(
            eventId = eventId,
            type = CommerceOutboxEventType.LIKE_COUNT_CHANGED_V1.name,
            aggregateType = CommerceOutboxAggregateType.PRODUCT.value,
            aggregateId = 123L,
            payload = payload,
        )
}
