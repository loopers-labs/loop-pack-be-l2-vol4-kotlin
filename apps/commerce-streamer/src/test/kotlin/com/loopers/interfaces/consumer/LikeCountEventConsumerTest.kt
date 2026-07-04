package com.loopers.interfaces.consumer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.config.kafka.KafkaConfig
import com.loopers.projection.like.application.ProductMetricsProjectionCommand
import com.loopers.projection.like.application.ProductMetricsProjectionException
import com.loopers.projection.like.application.ProductMetricsProjectionResult
import com.loopers.projection.like.application.ProductMetricsProjectionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import java.util.UUID

class LikeCountEventConsumerTest {
    @Test
    fun `relay_JSON_계약은_eventType을_역직렬화한다`() {
        val event = objectMapper.readValue<ProductMetricsKafkaEvent>(
            """
            {
              "eventId": "$EVENT_ID",
              "eventType": "$EVENT_TYPE",
              "productId": $PRODUCT_ID,
              "userId": $USER_ID,
              "delta": 1
            }
            """.trimIndent(),
        )

        assertThat(event.eventId).isEqualTo(UUID.fromString(EVENT_ID))
        assertThat(event.eventType).isEqualTo(EVENT_TYPE)
        assertThat(event.productId).isEqualTo(PRODUCT_ID)
        assertThat(event.userId).isEqualTo(USER_ID)
        assertThat(event.delta).isEqualTo(1)
    }

    @Test
    fun `기존_type_필드_페이로드도_역직렬화한다`() {
        val event = objectMapper.readValue<ProductMetricsKafkaEvent>(
            """
            {
              "eventId": "$EVENT_ID",
              "type": "$EVENT_TYPE",
              "productId": $PRODUCT_ID,
              "userId": $USER_ID,
              "delta": -1
            }
            """.trimIndent(),
        )

        assertThat(event.eventId).isEqualTo(UUID.fromString(EVENT_ID))
        assertThat(event.eventType).isEqualTo(EVENT_TYPE)
        assertThat(event.productId).isEqualTo(PRODUCT_ID)
        assertThat(event.userId).isEqualTo(USER_ID)
        assertThat(event.delta).isEqualTo(-1)
    }

    @Test
    fun `상품_metrics_이벤트_listener는_record_manual_ack_factory를_사용한다`() {
        val listener = LikeCountEventConsumer::class.java
            .getDeclaredMethod("consume", ProductMetricsKafkaEvent::class.java, Acknowledgment::class.java)
            .getAnnotation(KafkaListener::class.java)

        assertThat(listener.topics).containsExactly(
            "\${commerce-events.product-metrics.catalog-topic-name}",
            "\${commerce-events.product-metrics.order-topic-name}",
        )
        assertThat(listener.groupId).isEqualTo(CONSUMER_GROUP)
        assertThat(listener.containerFactory).isEqualTo(KafkaConfig.RECORD_LISTENER)
    }

    @Test
    fun `좋아요_수_이벤트_처리가_성공하면_projection_이후_ack한다`() {
        val projectionService = mockk<ProductMetricsProjectionService>()
        val commandSlot = slot<ProductMetricsProjectionCommand>()
        val acknowledgment = CountingAcknowledgment()
        every { projectionService.project(capture(commandSlot)) } answers {
            assertThat(acknowledgment.count).isZero()
            ProductMetricsProjectionResult.applied()
        }
        val consumer = LikeCountEventConsumer(projectionService, objectMapper)
        val eventId = UUID.randomUUID()

        consumer.consume(event(eventId = eventId, delta = 1), acknowledgment)

        assertThat(acknowledgment.count).isEqualTo(1)
        assertThat(commandSlot.captured.eventId).isEqualTo(eventId)
        assertThat(commandSlot.captured.consumerGroup).isEqualTo(CONSUMER_GROUP)
        assertThat(commandSlot.captured.eventType).isEqualTo(EVENT_TYPE)
        val delta = commandSlot.captured.deltas.single()
        assertThat(delta.productId).isEqualTo(PRODUCT_ID)
        assertThat(delta.likeDelta).isEqualTo(1)
        verify(exactly = 1) { projectionService.project(any()) }
    }

    @Test
    fun `중복_이벤트는_projection_noop_이후_ack한다`() {
        val projectionService = mockk<ProductMetricsProjectionService>()
        val acknowledgment = CountingAcknowledgment()
        every { projectionService.project(any()) } answers {
            assertThat(acknowledgment.count).isZero()
            ProductMetricsProjectionResult.duplicate()
        }
        val consumer = LikeCountEventConsumer(projectionService, objectMapper)

        consumer.consume(event(delta = -1), acknowledgment)

        assertThat(acknowledgment.count).isEqualTo(1)
        verify(exactly = 1) { projectionService.project(any()) }
    }

    @Test
    fun `projection_예외가_발생하면_ack하지_않고_예외를_전파한다`() {
        val projectionService = mockk<ProductMetricsProjectionService>()
        val acknowledgment = CountingAcknowledgment()
        every { projectionService.project(any()) } throws ProductMetricsProjectionException("projection failed")
        val consumer = LikeCountEventConsumer(projectionService, objectMapper)

        assertThrows<ProductMetricsProjectionException> {
            consumer.consume(event(delta = 1), acknowledgment)
        }

        assertThat(acknowledgment.count).isZero()
        verify(exactly = 1) { projectionService.project(any()) }
    }

    @Test
    fun `주문_결제_이벤트는_상품별_sales_delta로_변환한다`() {
        val projectionService = mockk<ProductMetricsProjectionService>()
        val commandSlot = slot<ProductMetricsProjectionCommand>()
        val acknowledgment = CountingAcknowledgment()
        every { projectionService.project(capture(commandSlot)) } returns ProductMetricsProjectionResult.applied()
        val consumer = LikeCountEventConsumer(projectionService, objectMapper)

        consumer.consume(
            ProductMetricsKafkaEvent(
                eventId = UUID.randomUUID(),
                eventType = "ORDER_PAID_V1",
                aggregateType = "ORDER",
                aggregateId = 100L,
                payload = """{"orderId":100,"items":[{"productId":10,"quantity":2},{"productId":11,"quantity":1}]}""",
                createdAt = "2026-07-03T09:00:00+09:00",
            ),
            acknowledgment,
        )

        assertThat(acknowledgment.count).isEqualTo(1)
        assertThat(commandSlot.captured.deltas).hasSize(2)
        assertThat(commandSlot.captured.deltas.map { it.salesDelta }).containsExactly(2, 1)
    }

    private fun event(
        eventId: UUID = UUID.randomUUID(),
        delta: Int,
    ): ProductMetricsKafkaEvent =
        ProductMetricsKafkaEvent(
            eventId = eventId,
            eventType = EVENT_TYPE,
            productId = PRODUCT_ID,
            userId = USER_ID,
            delta = delta,
        )

    private class CountingAcknowledgment : Acknowledgment {
        var count = 0
            private set

        override fun acknowledge() {
            count += 1
        }
    }

    companion object {
        private val objectMapper = jacksonObjectMapper()
        private const val EVENT_ID = "11111111-1111-1111-1111-111111111111"
        private const val CONSUMER_GROUP = "commerce-streamer-product-metrics"
        private const val EVENT_TYPE = "LIKE_COUNT_CHANGED_V1"
        private const val PRODUCT_ID = 10L
        private const val USER_ID = 20L
    }
}
