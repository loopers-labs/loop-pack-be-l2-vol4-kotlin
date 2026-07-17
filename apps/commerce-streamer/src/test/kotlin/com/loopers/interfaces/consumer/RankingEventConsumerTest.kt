package com.loopers.interfaces.consumer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.projection.ranking.application.RankingCarryOverProperties
import com.loopers.projection.ranking.application.RankingProjectionCommand
import com.loopers.projection.ranking.application.RankingProjectionService
import com.loopers.projection.ranking.application.RankingProperties
import com.loopers.projection.ranking.application.RankingRdbSyncProperties
import com.loopers.projection.ranking.application.RankingScorePolicy
import com.loopers.projection.ranking.application.RankingScoreProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment

class RankingEventConsumerTest {
    @Test
    fun `랭킹_이벤트_listener는_전용_그룹과_record_manual_ack_factory를_사용한다`() {
        val listener = RankingEventConsumer::class.java
            .getDeclaredMethod("consume", ProductMetricsKafkaEvent::class.java, Acknowledgment::class.java)
            .getAnnotation(KafkaListener::class.java)

        assertThat(listener.topics).containsExactly(
            "\${commerce-events.product-metrics.catalog-topic-name}",
            "\${commerce-events.product-metrics.order-topic-name}",
        )
        assertThat(listener.groupId).isEqualTo("commerce-streamer-ranking")
        assertThat(listener.containerFactory).isEqualTo(KafkaConfig.RECORD_LISTENER)
    }

    @Test
    fun `좋아요_증가_이벤트는_건당_1점_delta로_변환한_뒤_ack한다`() {
        val projectionService = mockk<RankingProjectionService>()
        val commandSlot = slot<RankingProjectionCommand>()
        val acknowledgment = CountingAcknowledgment()
        every { projectionService.project(capture(commandSlot)) } answers {
            assertThat(acknowledgment.count).isZero()
        }
        val consumer = consumer(projectionService)
        val eventId = UUID.randomUUID()

        consumer.consume(
            ProductMetricsKafkaEvent(
                eventId = eventId,
                eventType = "LIKE_COUNT_CHANGED_V1",
                aggregateType = "PRODUCT",
                aggregateId = 상품_ID,
                payload = """{"productId":$상품_ID,"userId":$사용자_ID,"delta":1}""",
                createdAt = "2026-07-17T10:00:00+09:00[Asia/Seoul]",
            ),
            acknowledgment,
        )

        assertThat(acknowledgment.count).isEqualTo(1)
        assertThat(commandSlot.captured.eventId).isEqualTo(eventId)
        val delta = commandSlot.captured.deltas.single()
        assertThat(delta.productId).isEqualTo(상품_ID)
        assertThat(delta.score).isEqualTo(1.0)
    }

    @Test
    fun `좋아요_취소_이벤트는_건당_마이너스_1점_delta로_변환한다`() {
        val projectionService = mockk<RankingProjectionService>(relaxed = true)
        val commandSlot = slot<RankingProjectionCommand>()
        every { projectionService.project(capture(commandSlot)) } returns Unit
        val consumer = consumer(projectionService)

        consumer.consume(likeEvent(delta = -1), CountingAcknowledgment())

        assertThat(commandSlot.captured.deltas.single().score).isEqualTo(-1.0)
    }

    @Test
    fun `주문_결제_이벤트는_수량과_무관하게_상품당_4점_delta로_변환한다`() {
        val projectionService = mockk<RankingProjectionService>()
        val commandSlot = slot<RankingProjectionCommand>()
        val acknowledgment = CountingAcknowledgment()
        every { projectionService.project(capture(commandSlot)) } returns Unit
        val consumer = consumer(projectionService)

        consumer.consume(
            ProductMetricsKafkaEvent(
                eventId = UUID.randomUUID(),
                eventType = "ORDER_PAID_V1",
                aggregateType = "ORDER",
                aggregateId = 100L,
                payload = """{"orderId":100,"items":[{"productId":10,"quantity":7},{"productId":11,"quantity":1}]}""",
            ),
            acknowledgment,
        )

        assertThat(acknowledgment.count).isEqualTo(1)
        assertThat(commandSlot.captured.deltas).hasSize(2)
        assertThat(commandSlot.captured.deltas.map { it.score }).containsExactly(4.0, 4.0)
        assertThat(commandSlot.captured.deltas.map { it.productId }).containsExactly(10L, 11L)
    }

    @Test
    fun `조회_이벤트는_projection_없이_ack한다`() {
        val projectionService = mockk<RankingProjectionService>()
        val acknowledgment = CountingAcknowledgment()
        val consumer = consumer(projectionService)

        consumer.consume(
            ProductMetricsKafkaEvent(
                eventId = UUID.randomUUID(),
                eventType = "PRODUCT_VIEWED_V1",
                aggregateType = "PRODUCT",
                aggregateId = 상품_ID,
                payload = """{"productId":$상품_ID}""",
            ),
            acknowledgment,
        )

        assertThat(acknowledgment.count).isEqualTo(1)
        verify(exactly = 0) { projectionService.project(any()) }
    }

    @Test
    fun `미지원_이벤트_타입은_projection_없이_ack한다`() {
        val projectionService = mockk<RankingProjectionService>()
        val acknowledgment = CountingAcknowledgment()
        val consumer = consumer(projectionService)

        consumer.consume(
            ProductMetricsKafkaEvent(
                eventId = UUID.randomUUID(),
                eventType = "ORDER_CREATED_V1",
                aggregateType = "ORDER",
                aggregateId = 100L,
            ),
            acknowledgment,
        )

        assertThat(acknowledgment.count).isEqualTo(1)
        verify(exactly = 0) { projectionService.project(any()) }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 2])
    fun `좋아요_증감값이_마이너스_1이나_1이_아니면_projection과_ack을_하지_않는다`(invalidDelta: Int) {
        val projectionService = mockk<RankingProjectionService>()
        val acknowledgment = CountingAcknowledgment()
        val consumer = consumer(projectionService)

        assertThrows<IllegalArgumentException> {
            consumer.consume(likeEvent(delta = invalidDelta), acknowledgment)
        }

        assertThat(acknowledgment.count).isZero()
        verify(exactly = 0) { projectionService.project(any()) }
    }

    @Test
    fun `projection_예외가_발생하면_ack하지_않고_예외를_전파한다`() {
        val projectionService = mockk<RankingProjectionService>()
        val acknowledgment = CountingAcknowledgment()
        every { projectionService.project(any()) } throws IllegalStateException("projection failed")
        val consumer = consumer(projectionService)

        assertThrows<IllegalStateException> {
            consumer.consume(likeEvent(delta = 1), acknowledgment)
        }

        assertThat(acknowledgment.count).isZero()
    }

    private fun consumer(projectionService: RankingProjectionService): RankingEventConsumer =
        RankingEventConsumer(projectionService, scorePolicy, objectMapper)

    private fun likeEvent(delta: Int): ProductMetricsKafkaEvent =
        ProductMetricsKafkaEvent(
            eventId = UUID.randomUUID(),
            eventType = "LIKE_COUNT_CHANGED_V1",
            productId = 상품_ID,
            userId = 사용자_ID,
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
        private val scorePolicy = RankingScorePolicy(
            RankingProperties(
                score = RankingScoreProperties(like = 1.0, order = 4.0),
                carryOver = RankingCarryOverProperties(
                    enabled = true,
                    cron = "0 55 23 * * *",
                    decay = 0.5,
                    minScore = 1.0,
                ),
                rdbSync = RankingRdbSyncProperties(enabled = true, fixedDelayMs = 600_000, topN = 1000),
            ),
        )
        private const val 상품_ID = 10L
        private const val 사용자_ID = 20L
    }
}
