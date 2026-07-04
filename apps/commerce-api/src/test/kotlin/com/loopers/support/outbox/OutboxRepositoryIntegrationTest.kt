package com.loopers.support.outbox

import com.loopers.utils.DatabaseCleanUp
import java.time.ZonedDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OutboxRepositoryIntegrationTest
    @Autowired
    constructor(
        private val outboxRepository: OutboxRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `이벤트는_UUID_eventId와_재시도_초기값을_저장한다`() {
            val eventId = UUID.randomUUID()

            val saved = outboxRepository.save(이벤트(eventId = eventId))

            assertThat(saved.id).isPositive()
            assertThat(saved.eventId).isEqualTo(eventId)
            assertThat(saved.type).isEqualTo(PAYMENT_STATUS_SYNC_REQUESTED)
            assertThat(saved.aggregateType).isEqualTo(PAYMENT_AGGREGATE)
            assertThat(saved.aggregateId).isEqualTo(1L)
            assertThat(saved.status).isEqualTo(OutboxEventStatus.PENDING)
            assertThat(saved.retryCount).isZero()
            assertThat(saved.nextRetryAt).isNull()
            assertThat(saved.lastError).isNull()
            assertThat(saved.publishedAt).isNull()
        }

        @Test
        fun `PENDING_상태의_특정_타입_이벤트만_조회한다`() {
            outboxRepository.save(이벤트(type = PAYMENT_STATUS_SYNC_REQUESTED, aggregateId = 1L))
            outboxRepository.save(이벤트(type = PAYMENT_APPROVED, aggregateId = 2L))

            val pending = outboxRepository.findPendingByType(PAYMENT_STATUS_SYNC_REQUESTED)

            assertThat(pending).hasSize(1)
            assertThat(pending.first().aggregateId).isEqualTo(1L)
        }

        @Test
        fun `발행대상_이벤트를_claim하면_같은_행은_다시_claim되지_않는다`() {
            outboxRepository.save(이벤트(type = PAYMENT_APPROVED, aggregateId = 1L))
            val now = ZonedDateTime.now()

            val firstClaim = outboxRepository.claimPublishable(
                publishableTypes = setOf(PAYMENT_APPROVED),
                now = now,
                limit = 10,
            )
            val secondClaim = outboxRepository.claimPublishable(
                publishableTypes = setOf(PAYMENT_APPROVED),
                now = now,
                limit = 10,
            )

            assertThat(firstClaim).hasSize(1)
            assertThat(firstClaim.first().status).isEqualTo(OutboxEventStatus.PUBLISHING)
            assertThat(secondClaim).isEmpty()
        }

        @Test
        fun `Kafka_relay는_지원하는_이벤트_type만_claim한다`() {
            val syncRequest = outboxRepository.save(이벤트(type = PAYMENT_STATUS_SYNC_REQUESTED, aggregateId = 1L))
            val approved = outboxRepository.save(이벤트(type = PAYMENT_APPROVED, aggregateId = 2L))

            val claimed = outboxRepository.claimPublishable(
                publishableTypes = setOf(PAYMENT_APPROVED),
                now = ZonedDateTime.now(),
                limit = 10,
            )

            assertThat(claimed.map { it.eventId }).containsExactly(approved.eventId)
            assertThat(outboxRepository.findByEventIdOrNull(syncRequest.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PENDING)
            assertThat(outboxRepository.findByEventIdOrNull(approved.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PUBLISHING)
        }

        @Test
        fun `발행완료_마킹은_PUBLISHED와_publishedAt을_기록한다`() {
            val saved = outboxRepository.save(이벤트(type = PAYMENT_APPROVED, aggregateId = 1L))
            val publishedAt = ZonedDateTime.now().plusSeconds(1)

            outboxRepository.markPublished(saved.eventId, publishedAt)

            val updated = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(updated?.status).isEqualTo(OutboxEventStatus.PUBLISHED)
            assertThat(updated?.publishedAt).isEqualTo(publishedAt)
            assertThat(outboxRepository.findPendingByType(PAYMENT_APPROVED)).isEmpty()
        }

        @Test
        fun `발행실패_마킹은_재시도_메타데이터를_기록하고_재시도시각_전에는_claim되지_않는다`() {
            val saved = outboxRepository.save(이벤트(type = PAYMENT_APPROVED, aggregateId = 1L))
            val nextRetryAt = ZonedDateTime.now().plusMinutes(1)

            outboxRepository.markFailed(saved.eventId, "broker timeout", nextRetryAt)

            val failed = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(failed?.status).isEqualTo(OutboxEventStatus.FAILED)
            assertThat(failed?.retryCount).isEqualTo(1)
            assertThat(failed?.lastError).isEqualTo("broker timeout")
            assertThat(failed?.nextRetryAt).isEqualTo(nextRetryAt)
            assertThat(
                outboxRepository.claimPublishable(
                    publishableTypes = setOf(PAYMENT_APPROVED),
                    now = nextRetryAt.minusSeconds(1),
                    limit = 10,
                ),
            )
                .isEmpty()

            val retryable = outboxRepository.claimPublishable(
                publishableTypes = setOf(PAYMENT_APPROVED),
                now = nextRetryAt.plusSeconds(1),
                limit = 10,
            )

            assertThat(retryable).hasSize(1)
            assertThat(retryable.first().status).isEqualTo(OutboxEventStatus.PUBLISHING)
            assertThat(retryable.first().retryCount).isEqualTo(1)
        }

        private fun 이벤트(
            eventId: UUID = UUID.randomUUID(),
            type: String = PAYMENT_STATUS_SYNC_REQUESTED,
            aggregateId: Long = 1L,
        ): OutboxEventModel =
            OutboxEventModel(
                eventId = eventId,
                type = type,
                aggregateType = PAYMENT_AGGREGATE,
                aggregateId = aggregateId,
                payload = """{"aggregateId":$aggregateId}""",
            )

        private companion object {
            const val PAYMENT_STATUS_SYNC_REQUESTED = "PAYMENT_STATUS_SYNC_REQUESTED"
            const val PAYMENT_APPROVED = "PAYMENT_APPROVED"
            const val PAYMENT_AGGREGATE = "PAYMENT"
        }
    }
