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
            outboxRepository.save(이벤트(type = PAYMENT_RESULT_RECORDED, aggregateId = 2L))

            val pending = outboxRepository.findPendingByType(PAYMENT_STATUS_SYNC_REQUESTED)

            assertThat(pending).hasSize(1)
            assertThat(pending.first().aggregateId).isEqualTo(1L)
        }

        @Test
        fun `발행대상_이벤트를_claim하면_같은_행은_다시_claim되지_않는다`() {
            outboxRepository.save(발행_이벤트(aggregateId = 1L))
            val now = 기준_시각

            val firstClaim = outboxRepository.claimPublishable(
                now = now,
                claimExpiredBefore = now.minusMinutes(5),
                limit = 10,
            )
            val secondClaim = outboxRepository.claimPublishable(
                now = now,
                claimExpiredBefore = now.minusMinutes(5),
                limit = 10,
            )

            assertThat(firstClaim).hasSize(1)
            assertThat(firstClaim.first().status).isEqualTo(OutboxEventStatus.PUBLISHING)
            assertThat(firstClaim.first().claimId).isNotNull()
            assertThat(firstClaim.first().claimedAt).isEqualTo(now)
            assertThat(secondClaim).isEmpty()
        }

        @Test
        fun `Kafka_relay는_저장된_발행경로가_있는_이벤트만_claim한다`() {
            val syncRequest = outboxRepository.save(이벤트(type = PAYMENT_STATUS_SYNC_REQUESTED, aggregateId = 1L))
            val orderPaid = outboxRepository.save(발행_이벤트(aggregateId = 2L))

            val claimed = outboxRepository.claimPublishable(
                now = 기준_시각,
                claimExpiredBefore = 기준_시각.minusMinutes(5),
                limit = 10,
            )

            assertThat(claimed.map { it.eventId }).containsExactly(orderPaid.eventId)
            assertThat(outboxRepository.findByEventIdOrNull(syncRequest.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PENDING)
            assertThat(outboxRepository.findByEventIdOrNull(orderPaid.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PUBLISHING)
        }

        @Test
        fun `발행완료_마킹은_PUBLISHED와_publishedAt을_기록한다`() {
            val saved = outboxRepository.save(발행_이벤트(aggregateId = 1L))
            val claimed = outboxRepository.claimPublishable(
                now = 기준_시각,
                claimExpiredBefore = 기준_시각.minusMinutes(5),
                limit = 1,
            ).single()
            val publishedAt = 기준_시각.plusSeconds(1)

            val marked = outboxRepository.markPublished(saved.eventId, claimed.claimId!!, publishedAt)

            assertThat(marked).isTrue()
            val updated = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(updated?.status).isEqualTo(OutboxEventStatus.PUBLISHED)
            assertThat(updated?.publishedAt).isEqualTo(publishedAt)
            assertThat(updated?.claimId).isNull()
            assertThat(updated?.claimedAt).isNull()
            assertThat(outboxRepository.findPendingByType(ORDER_PAID)).isEmpty()
        }

        @Test
        fun `발행실패_마킹은_재시도_메타데이터를_기록하고_재시도시각_전에는_claim되지_않는다`() {
            val saved = outboxRepository.save(발행_이벤트(aggregateId = 1L))
            val claimed = outboxRepository.claimPublishable(
                now = 기준_시각,
                claimExpiredBefore = 기준_시각.minusMinutes(5),
                limit = 1,
            ).single()
            val nextRetryAt = 기준_시각.plusMinutes(1)

            val marked = outboxRepository.markFailed(
                eventId = saved.eventId,
                claimId = claimed.claimId!!,
                error = "broker timeout",
                nextRetryAt = nextRetryAt,
                maxPublishAttempts = 5,
            )

            assertThat(marked).isTrue()
            val failed = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(failed?.status).isEqualTo(OutboxEventStatus.FAILED)
            assertThat(failed?.retryCount).isEqualTo(1)
            assertThat(failed?.lastError).isEqualTo("broker timeout")
            assertThat(failed?.nextRetryAt).isEqualTo(nextRetryAt)
            assertThat(
                outboxRepository.claimPublishable(
                    now = nextRetryAt.minusSeconds(1),
                    claimExpiredBefore = nextRetryAt.minusMinutes(5),
                    limit = 10,
                ),
            )
                .isEmpty()

            val retryable = outboxRepository.claimPublishable(
                now = nextRetryAt,
                claimExpiredBefore = nextRetryAt.minusMinutes(5),
                limit = 10,
            )

            assertThat(retryable).hasSize(1)
            assertThat(retryable.first().status).isEqualTo(OutboxEventStatus.PUBLISHING)
            assertThat(retryable.first().retryCount).isEqualTo(1)
        }

        @Test
        fun `발행이_5회_실패하면_DEAD로_전이하고_더는_claim하지_않는다`() {
            val saved = outboxRepository.save(발행_이벤트(aggregateId = 9L))

            repeat(5) { index ->
                val attemptedAt = 기준_시각.plusMinutes(index.toLong())
                val claimed = outboxRepository.claimPublishable(
                    now = attemptedAt,
                    claimExpiredBefore = attemptedAt.minusMinutes(5),
                    limit = 1,
                ).single()
                assertThat(
                    outboxRepository.markFailed(
                        eventId = saved.eventId,
                        claimId = claimed.claimId!!,
                        error = "broker unavailable ${index + 1}",
                        nextRetryAt = attemptedAt.plusMinutes(1),
                        maxPublishAttempts = 5,
                    ),
                ).isTrue()
            }

            val dead = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(dead?.status).isEqualTo(OutboxEventStatus.DEAD)
            assertThat(dead?.retryCount).isEqualTo(5)
            assertThat(dead?.nextRetryAt).isNull()
            assertThat(dead?.lastError).isEqualTo("broker unavailable 5")
            assertThat(
                outboxRepository.claimPublishable(
                    now = 기준_시각.plusYears(1),
                    claimExpiredBefore = 기준_시각.plusYears(1).minusMinutes(5),
                    limit = 1,
                ),
            ).isEmpty()
        }

        @Test
        fun `claim_직후_중단되면_lease_만료_경계에서_같은_이벤트를_새_claim으로_재선점한다`() {
            val saved = outboxRepository.save(발행_이벤트(aggregateId = 7L))
            val firstClaim = outboxRepository.claimPublishable(
                now = 기준_시각,
                claimExpiredBefore = 기준_시각.minusMinutes(5),
                limit = 1,
            ).single()

            val beforeExpiry = outboxRepository.claimPublishable(
                now = 기준_시각.plusMinutes(5).minusSeconds(1),
                claimExpiredBefore = 기준_시각.minusSeconds(1),
                limit = 1,
            )
            val reclaimed = outboxRepository.claimPublishable(
                now = 기준_시각.plusMinutes(5),
                claimExpiredBefore = 기준_시각,
                limit = 1,
            ).single()

            assertThat(beforeExpiry).isEmpty()
            assertThat(reclaimed.eventId).isEqualTo(saved.eventId)
            assertThat(reclaimed.claimId).isNotEqualTo(firstClaim.claimId)
            assertThat(reclaimed.claimedAt).isEqualTo(기준_시각.plusMinutes(5))
            assertThat(reclaimed.topicName).isEqualTo(firstClaim.topicName)
            assertThat(reclaimed.partitionKey).isEqualTo(firstClaim.partitionKey)
            assertThat(reclaimed.payload).isEqualTo(firstClaim.payload)
            assertThat(reclaimed.createdAt).isEqualTo(firstClaim.createdAt)
        }

        @Test
        fun `재선점_후_이전_claim_소유자의_늦은_완료와_실패는_현재_상태를_바꾸지_못한다`() {
            val saved = outboxRepository.save(발행_이벤트(aggregateId = 8L))
            val firstClaim = outboxRepository.claimPublishable(
                now = 기준_시각,
                claimExpiredBefore = 기준_시각.minusMinutes(5),
                limit = 1,
            ).single()
            val currentClaim = outboxRepository.claimPublishable(
                now = 기준_시각.plusMinutes(5),
                claimExpiredBefore = 기준_시각,
                limit = 1,
            ).single()

            val stalePublished = outboxRepository.markPublished(
                eventId = saved.eventId,
                claimId = firstClaim.claimId!!,
                publishedAt = 기준_시각.plusMinutes(5).plusSeconds(1),
            )
            val staleFailed = outboxRepository.markFailed(
                eventId = saved.eventId,
                claimId = firstClaim.claimId,
                error = "late timeout",
                nextRetryAt = 기준_시각.plusMinutes(6),
                maxPublishAttempts = 5,
            )

            val unchanged = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(stalePublished).isFalse()
            assertThat(staleFailed).isFalse()
            assertThat(unchanged?.status).isEqualTo(OutboxEventStatus.PUBLISHING)
            assertThat(unchanged?.claimId).isEqualTo(currentClaim.claimId)
            assertThat(unchanged?.retryCount).isZero()
            assertThat(unchanged?.publishedAt).isNull()
            assertThat(
                outboxRepository.markPublished(
                    eventId = saved.eventId,
                    claimId = currentClaim.claimId!!,
                    publishedAt = 기준_시각.plusMinutes(5).plusSeconds(2),
                ),
            ).isTrue()
        }

        private fun 이벤트(
            eventId: UUID = UUID.randomUUID(),
            type: String = PAYMENT_STATUS_SYNC_REQUESTED,
            aggregateId: Long = 1L,
        ): OutboxEventModel =
            OutboxEventModel.internal(
                eventId = eventId,
                type = type,
                aggregateType = PAYMENT_AGGREGATE,
                aggregateId = aggregateId,
                payload = """{"aggregateId":$aggregateId}""",
            )

        private fun 발행_이벤트(aggregateId: Long): OutboxEventModel =
            OutboxEventModel.publishable(
                type = ORDER_PAID,
                aggregateType = "ORDER",
                aggregateId = aggregateId,
                topicName = "order-events",
                partitionKey = aggregateId.toString(),
                payload = """{"aggregateId":$aggregateId}""",
            )

        private companion object {
            val 기준_시각: ZonedDateTime = ZonedDateTime.parse("2026-07-17T10:00:00+09:00[Asia/Seoul]")
            const val PAYMENT_STATUS_SYNC_REQUESTED = "PAYMENT_STATUS_SYNC_REQUESTED"
            const val PAYMENT_RESULT_RECORDED = "PAYMENT_RESULT_RECORDED"
            const val ORDER_PAID = "ORDER_PAID_V1"
            const val PAYMENT_AGGREGATE = "PAYMENT"
        }
    }
