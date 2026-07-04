package com.loopers.support.outbox.relay

import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxEventStatus
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.event.CommerceOutboxAggregateType
import com.loopers.support.outbox.event.CommerceOutboxEventType
import com.loopers.utils.DatabaseCleanUp
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest(
    properties = [
        "commerce-events.outbox-relay.enabled=false",
    ],
)
@Import(OutboxRelayIntegrationTestConfig::class)
class OutboxRelayIntegrationTest
    @Autowired
    constructor(
        private val outboxRelay: OutboxRelay,
        private val outboxRepository: OutboxRepository,
        private val publisher: RecordingOutboxEventPublisher,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            publisher.reset()
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `claim된_이벤트는_트랜잭션_밖에서_발행되고_ack_후_PUBLISHED로_마킹된다`() {
            val saved = outboxRepository.save(likeCountEvent())

            val published = outboxRelay.publishOnce()

            assertThat(published).isEqualTo(1)
            assertThat(publisher.calls).hasSize(1)
            assertThat(publisher.calls.first().transactionActive).isFalse()
            assertThat(publisher.calls.first().event.status).isEqualTo(OutboxEventStatus.PUBLISHING)
            val updated = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(updated?.status).isEqualTo(OutboxEventStatus.PUBLISHED)
            assertThat(updated?.publishedAt).isNotNull()
        }

        @Test
        fun `relay는_like_count로_한정하지_않고_지원되는_outbox_이벤트를_발행한다`() {
            val likeEvent = outboxRepository.save(likeCountEvent())
            val orderEvent = outboxRepository.save(orderPaidEvent())

            val published = outboxRelay.publishOnce()

            assertThat(published).isEqualTo(2)
            assertThat(publisher.calls.map { it.event.eventId })
                .containsExactly(likeEvent.eventId, orderEvent.eventId)
            assertThat(outboxRepository.findByEventIdOrNull(likeEvent.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PUBLISHED)
            assertThat(outboxRepository.findByEventIdOrNull(orderEvent.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PUBLISHED)
        }

        @Test
        fun `relay는_Kafka_라우팅을_지원하지_않는_내부_outbox를_claim하지_않는다`() {
            val syncRequest = outboxRepository.save(paymentStatusSyncEvent())
            val orderEvent = outboxRepository.save(orderPaidEvent())

            val published = outboxRelay.publishOnce()

            assertThat(published).isEqualTo(1)
            assertThat(publisher.calls.map { it.event.eventId }).containsExactly(orderEvent.eventId)
            assertThat(outboxRepository.findByEventIdOrNull(syncRequest.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PENDING)
            assertThat(outboxRepository.findByEventIdOrNull(orderEvent.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PUBLISHED)
        }

        @Test
        fun `브로커_ack가_실패하면_이벤트는_재시도_가능한_FAILED로_남는다`() {
            publisher.failWith = IllegalStateException("broker unavailable")
            val saved = outboxRepository.save(likeCountEvent())
            val beforePublish = ZonedDateTime.now()

            val published = outboxRelay.publishOnce()

            assertThat(published).isZero()
            assertThat(publisher.calls).hasSize(1)
            assertThat(publisher.calls.first().transactionActive).isFalse()
            val updated = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(updated?.status).isEqualTo(OutboxEventStatus.FAILED)
            assertThat(updated?.retryCount).isEqualTo(1)
            assertThat(updated?.lastError).contains("broker unavailable")
            assertThat(updated?.nextRetryAt).isAfter(beforePublish)
            assertThat(updated?.publishedAt).isNull()
        }

        private fun likeCountEvent(): OutboxEventModel =
            OutboxEventModel(
                type = "LIKE_COUNT_CHANGED_V1",
                aggregateType = "PRODUCT",
                aggregateId = 10L,
                payload = """{"productId":10,"userId":20,"delta":1}""",
            )

        private fun orderPaidEvent(): OutboxEventModel =
            OutboxEventModel(
                type = CommerceOutboxEventType.ORDER_PAID_V1.name,
                aggregateType = CommerceOutboxAggregateType.ORDER.value,
                aggregateId = 20L,
                payload = """{"orderId":20,"items":[{"productId":10,"quantity":2}]}""",
            )

        private fun paymentStatusSyncEvent(): OutboxEventModel =
            OutboxEventModel(
                type = "PAYMENT_STATUS_SYNC_REQUESTED",
                aggregateType = "PAYMENT",
                aggregateId = 30L,
                payload = """{"paymentId":30,"orderId":40}""",
            )
    }
