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
        "commerce-events.outbox-relay.relay-claim-lease=PT5M",
        "commerce-events.outbox-relay.relay-retry-delay=PT1M",
        "commerce-events.outbox-relay.max-publish-attempts=5",
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
        fun `relay는_내부_payment_outbox를_claim하지_않는다`() {
            val syncRequest = outboxRepository.save(paymentStatusSyncEvent())
            val approved = outboxRepository.save(internalPaymentEvent("PAYMENT_APPROVED"))
            val failed = outboxRepository.save(internalPaymentEvent("PAYMENT_FAILED"))
            val orderEvent = outboxRepository.save(orderPaidEvent())

            val published = outboxRelay.publishOnce()

            assertThat(published).isEqualTo(1)
            assertThat(publisher.calls.map { it.event.eventId }).containsExactly(orderEvent.eventId)
            assertThat(outboxRepository.findByEventIdOrNull(syncRequest.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PENDING)
            assertThat(outboxRepository.findByEventIdOrNull(approved.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PENDING)
            assertThat(outboxRepository.findByEventIdOrNull(failed.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PENDING)
            assertThat(outboxRepository.findByEventIdOrNull(orderEvent.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PUBLISHED)
        }

        @Test
        fun `브로커_ack가_실패하면_이벤트는_재시도_가능한_FAILED로_남는다`() {
            publisher.failWith = IllegalStateException("broker unavailable")
            val saved = outboxRepository.save(likeCountEvent())

            val firstPublished = outboxRelay.publishOnce(기준_시각)

            assertThat(firstPublished).isZero()
            assertThat(publisher.calls).hasSize(1)
            assertThat(publisher.calls.first().transactionActive).isFalse()
            val updated = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(updated?.status).isEqualTo(OutboxEventStatus.FAILED)
            assertThat(updated?.retryCount).isEqualTo(1)
            assertThat(updated?.lastError).contains("broker unavailable")
            assertThat(updated?.nextRetryAt).isEqualTo(기준_시각.plusMinutes(1))
            assertThat(updated?.publishedAt).isNull()

            publisher.failWith = null
            assertThat(outboxRelay.publishOnce(기준_시각.plusMinutes(1).minusSeconds(1))).isZero()
            assertThat(outboxRelay.publishOnce(기준_시각.plusMinutes(1))).isEqualTo(1)

            assertThat(publisher.calls).hasSize(2)
            assertThat(publisher.calls.map { it.event.eventId }).containsOnly(saved.eventId)
            assertThat(publisher.calls.map { it.event.topicName }).containsOnly("catalog-events")
            assertThat(publisher.calls.map { it.event.partitionKey }).containsOnly("10")
            assertThat(publisher.calls.map { it.event.payload }).containsOnly(saved.payload)
            assertThat(publisher.calls).allMatch { !it.transactionActive }
            assertThat(outboxRepository.findByEventIdOrNull(saved.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PUBLISHED)
        }

        @Test
        fun `브로커_ack가_5회_실패하면_DEAD로_격리하고_재발행하지_않는다`() {
            publisher.failWith = IllegalStateException("permanent broker failure")
            val saved = outboxRepository.save(likeCountEvent())

            repeat(5) { index ->
                assertThat(outboxRelay.publishOnce(기준_시각.plusMinutes(index.toLong()))).isZero()
            }

            val dead = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(dead?.status).isEqualTo(OutboxEventStatus.DEAD)
            assertThat(dead?.retryCount).isEqualTo(5)
            assertThat(dead?.nextRetryAt).isNull()
            assertThat(dead?.lastError).contains("permanent broker failure")
            assertThat(publisher.calls).hasSize(5)

            assertThat(outboxRelay.publishOnce(기준_시각.plusYears(1))).isZero()
            assertThat(publisher.calls).hasSize(5)
        }

        @Test
        fun `broker_ack_뒤_상태저장_전_중단되면_lease_만료_후_동일_envelope를_재발행한다`() {
            val saved = outboxRepository.save(likeCountEvent())
            val firstClaim = outboxRepository.claimPublishable(
                now = 기준_시각,
                claimExpiredBefore = 기준_시각.minusMinutes(5),
                limit = 1,
            ).single()
            publisher.publish(firstClaim)

            assertThat(outboxRelay.publishOnce(기준_시각.plusMinutes(5).minusSeconds(1))).isZero()
            assertThat(outboxRelay.publishOnce(기준_시각.plusMinutes(5))).isEqualTo(1)

            assertThat(publisher.calls).hasSize(2)
            val retransmitted = publisher.calls.last().event
            assertThat(publisher.calls).allMatch { !it.transactionActive }
            assertThat(retransmitted.eventId).isEqualTo(saved.eventId)
            assertThat(retransmitted.claimId).isNotEqualTo(firstClaim.claimId)
            assertThat(retransmitted.topicName).isEqualTo(firstClaim.topicName)
            assertThat(retransmitted.partitionKey).isEqualTo(firstClaim.partitionKey)
            assertThat(retransmitted.payload).isEqualTo(firstClaim.payload)
            assertThat(retransmitted.createdAt).isEqualTo(firstClaim.createdAt)
            assertThat(outboxRepository.findByEventIdOrNull(saved.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PUBLISHED)
        }

        private fun likeCountEvent(): OutboxEventModel =
            OutboxEventModel(
                type = "LIKE_COUNT_CHANGED_V1",
                aggregateType = "PRODUCT",
                aggregateId = 10L,
                topicName = "catalog-events",
                partitionKey = "10",
                payload = """{"productId":10,"userId":20,"delta":1}""",
            )

        private fun orderPaidEvent(): OutboxEventModel =
            OutboxEventModel(
                type = CommerceOutboxEventType.ORDER_PAID_V1.name,
                aggregateType = CommerceOutboxAggregateType.ORDER.value,
                aggregateId = 20L,
                topicName = "order-events",
                partitionKey = "20",
                payload = """{"orderId":20,"items":[{"productId":10,"quantity":2}]}""",
            )

        private fun internalPaymentEvent(type: String): OutboxEventModel =
            OutboxEventModel(
                type = type,
                aggregateType = "PAYMENT",
                aggregateId = 31L,
                topicName = null,
                partitionKey = null,
                payload = """{"paymentId":31,"orderId":40}""",
            )

        private fun paymentStatusSyncEvent(): OutboxEventModel =
            OutboxEventModel(
                type = "PAYMENT_STATUS_SYNC_REQUESTED",
                aggregateType = "PAYMENT",
                aggregateId = 30L,
                payload = """{"paymentId":30,"orderId":40}""",
            )

        private companion object {
            val 기준_시각: ZonedDateTime = ZonedDateTime.parse("2026-07-17T10:00:00+09:00[Asia/Seoul]")
        }
    }
