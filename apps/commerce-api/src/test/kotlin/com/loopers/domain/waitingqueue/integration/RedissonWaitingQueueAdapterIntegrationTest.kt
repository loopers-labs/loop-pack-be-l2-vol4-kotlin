package com.loopers.domain.waitingqueue.integration

import com.loopers.domain.waitingqueue.application.WaitingQueueFacade
import com.loopers.domain.waitingqueue.model.AdmissionTokenCandidate
import com.loopers.domain.waitingqueue.model.WaitingQueueEntryModel
import com.loopers.domain.waitingqueue.port.TokenValidationResult
import com.loopers.domain.waitingqueue.port.WaitingQueuePort
import com.loopers.testcontainers.RedisTestContainerInitializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = [
        "commerce.waiting-queue.token-ttl=PT5M",
        "commerce.waiting-queue.scheduler-delay=PT10S",
        "commerce.waiting-queue.admission-batch-size=2",
        "commerce.waiting-queue.scheduler-jitter-max=PT0S",
        "commerce.waiting-queue.polling-interval=PT2S",
        "commerce.waiting-queue.token-prefix=entrance_",
        "commerce.waiting-queue.redis-key-prefix=round8",
        "commerce.waiting-queue.redis-keys.entries=waiting-users",
        "commerce.waiting-queue.redis-keys.sequence=waiting-sequence",
        "commerce.waiting-queue.redis-keys.user-admission-prefix=user-admission",
        "commerce.waiting-queue.redis-keys.token-admission-prefix=token-admission",
    ],
)
@ContextConfiguration(initializers = [RedisTestContainerInitializer::class])
class RedissonWaitingQueueAdapterIntegrationTest
    @Autowired
    constructor(
        private val waitingQueuePort: WaitingQueuePort,
        private val waitingQueueFacade: WaitingQueueFacade,
        private val redissonClient: RedissonClient,
    ) {
        @AfterEach
        fun tearDown() {
            redissonClient.keys.flushdb()
        }

        @Test
        fun `대기열_포트는_ZSET의_1_based_순번을_조회하는_메소드를_노출한다`() {
            val now = Instant.now()
            waitingQueuePort.enqueueIfAbsent(userId = 1L, now = now)
            waitingQueuePort.enqueueIfAbsent(userId = 2L, now = now)
            waitingQueuePort.enqueueIfAbsent(userId = 3L, now = now)

            assertThat(waitingQueuePort.findPosition(2L)).isEqualTo(2L)
        }

        @Test
        fun `예상_대기_시간은_현재_순번이_입장할_스케줄러_batch_주기를_올림해_계산한다`() {
            val now = Instant.now()
            waitingQueuePort.enqueueIfAbsent(userId = 1L, now = now)
            waitingQueuePort.enqueueIfAbsent(userId = 2L, now = now)
            waitingQueuePort.enqueueIfAbsent(userId = 3L, now = now)

            assertThat(waitingQueuePort.findState(1L, now)?.estimatedWaitSeconds).isEqualTo(10L)
            assertThat(waitingQueuePort.findState(2L, now)?.estimatedWaitSeconds).isEqualTo(10L)
            assertThat(waitingQueuePort.findState(3L, now)?.estimatedWaitSeconds).isEqualTo(20L)
        }

        @Test
        fun `서로_다른_사용자의_동시_진입은_유일한_sequence와_일관된_순번을_보장한다`() {
            val userIds = (1L..20L).toList()
            val executor = Executors.newFixedThreadPool(userIds.size)
            val start = CountDownLatch(1)

            try {
                val futures = userIds.map { userId ->
                    executor.submit<WaitingQueueEntryModel> {
                        start.await()
                        waitingQueuePort.enqueueIfAbsent(userId, Instant.now())
                    }
                }
                start.countDown()
                val entries = futures.map { it.get(5, TimeUnit.SECONDS) }

                assertThat(entries.map { it.sequence }).doesNotHaveDuplicates()
                entries.sortedBy { it.sequence }.forEachIndexed { index, entry ->
                    assertThat(waitingQueuePort.findPosition(entry.userId)).isEqualTo(index + 1L)
                }
                assertThat(waitingQueuePort.findState(entries.first().userId, Instant.now())?.totalWaiting)
                    .isEqualTo(userIds.size.toLong())
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `같은_사용자의_동시_진입은_ZSET_member를_하나만_만든다`() {
            val executor = Executors.newFixedThreadPool(10)
            val start = CountDownLatch(1)

            try {
                val futures = (1..10).map {
                    executor.submit<WaitingQueueEntryModel> {
                        start.await()
                        waitingQueuePort.enqueueIfAbsent(userId = 1L, now = Instant.now())
                    }
                }
                start.countDown()
                val entries = futures.map { it.get(5, TimeUnit.SECONDS) }

                assertThat(entries.map { it.sequence }).containsOnly(entries.first().sequence)
                assertThat(redissonClient.getScoredSortedSet<String>("round8:waiting-users").size())
                    .isEqualTo(1)
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `batch_크기를_초과한_사용자는_다음_입장_차수에_순서대로_남는다`() {
            val now = Instant.now()
            (1L..5L).forEach { waitingQueuePort.enqueueIfAbsent(it, now) }

            val admitted = waitingQueuePort.admitNext(
                candidates = admissionCandidates(count = 2, now = now, ttl = Duration.ofMinutes(5)),
                tokenTtl = Duration.ofMinutes(5),
                now = now,
            )

            assertThat(admitted.map { it.userId }).containsExactly(1L, 2L)
            assertThat(waitingQueuePort.findPosition(1L)).isNull()
            assertThat(waitingQueuePort.findPosition(3L)).isEqualTo(1L)
            assertThat(waitingQueuePort.findPosition(5L)).isEqualTo(3L)
            assertThat(waitingQueuePort.findState(3L, now)?.totalWaiting).isEqualTo(3L)
        }

        @Test
        fun `Redis_TTL이_지나면_입장_토큰은_무효가_되고_사용자는_재진입할_수_있다`() {
            val now = Instant.now()
            val ttl = Duration.ofMillis(200)
            val token = "short-lived-token"
            waitingQueuePort.enqueueIfAbsent(userId = 1L, now = now)
            waitingQueuePort.admitNext(
                candidates = listOf(AdmissionTokenCandidate(token, now, now.plus(ttl))),
                tokenTtl = ttl,
                now = now,
            )

            Thread.sleep(300)

            assertThat(waitingQueuePort.validateToken(1L, token, "ttl-key", Instant.now()).isAllowed).isFalse()
            assertThat(waitingQueuePort.findState(1L, Instant.now())).isNull()
            assertThat(waitingQueuePort.enqueueIfAbsent(1L, Instant.now()).status.name).isEqualTo("WAITING")
        }

        @Test
        fun `설정한_Redis_key와_토큰_prefix가_실제_대기열과_입장_토큰에_사용된다`() {
            val now = Instant.now()
            waitingQueuePort.enqueueIfAbsent(userId = 1L, now = now)

            waitingQueueFacade.admitBatch()

            val state = requireNotNull(waitingQueuePort.findState(userId = 1L, now = now))
            assertThat(state.token).startsWith("entrance_")
            assertThat(redissonClient.getScoredSortedSet<String>("round8:waiting-users").isEmpty).isTrue()
            assertThat(redissonClient.getMap<String, String>("round8:token-admission:${state.token}").isExists)
                .isTrue()
        }

        @Test
        fun `PROCESSING_토큰의_같은_멱등키_재검증은_새_예약과_구분된다`() {
            val now = Instant.now()
            val token = "same-idempotency-token"
            waitingQueuePort.enqueueIfAbsent(userId = 1L, now = now)
            waitingQueuePort.admitNext(
                candidates = listOf(AdmissionTokenCandidate(token, now, now.plusSeconds(60))),
                tokenTtl = Duration.ofSeconds(60),
                now = now,
            )

            val first = waitingQueuePort.validateToken(1L, token, "same-key", now)
            val concurrentRetry = waitingQueuePort.validateToken(1L, token, "same-key", now)

            assertThat(first.status).isEqualTo(TokenValidationResult.Status.VALID)
            assertThat(concurrentRetry.status)
                .isEqualTo(TokenValidationResult.Status.PROCESSING_BY_SAME_IDEMPOTENCY_KEY)
        }

        @Test
        fun `다른_사용자는_타인의_입장_토큰을_예약할_수_없고_원소유자의_예약은_유지된다`() {
            val now = Instant.now()
            val token = "owner-bound-token"
            waitingQueuePort.enqueueIfAbsent(userId = 1L, now = now)
            waitingQueuePort.admitNext(
                candidates = listOf(AdmissionTokenCandidate(token, now, now.plusSeconds(60))),
                tokenTtl = Duration.ofSeconds(60),
                now = now,
            )

            val otherUser = waitingQueuePort.validateToken(2L, token, "other-user-key", now)
            val owner = waitingQueuePort.validateToken(1L, token, "owner-key", now)

            assertThat(otherUser.status).isEqualTo(TokenValidationResult.Status.INVALID)
            assertThat(owner.status).isEqualTo(TokenValidationResult.Status.VALID)
        }

        @Test
        fun `예약하지_않은_ACTIVE_토큰은_직접_consume할_수_없다`() {
            val now = Instant.now()
            val token = "active-token"
            waitingQueuePort.enqueueIfAbsent(userId = 1L, now = now)
            waitingQueuePort.admitNext(
                candidates = listOf(AdmissionTokenCandidate(token, now, now.plusSeconds(60))),
                tokenTtl = Duration.ofSeconds(60),
                now = now,
            )

            val consumed: Boolean = waitingQueuePort.consumeToken(1L, token, "unreserved-key")
            val reservationAfterRejectedConsume = waitingQueuePort.validateToken(1L, token, "valid-key", now)

            assertThat(consumed).isFalse()
            assertThat(reservationAfterRejectedConsume.status).isEqualTo(TokenValidationResult.Status.VALID)
        }

        @Test
        fun `이미_발급된_토큰과_충돌한_admit은_기존_binding을_덮어쓰거나_대기자를_유실시키지_않는다`() {
            val now = Instant.now()
            val token = "collision-token"
            val ttl = Duration.ofMinutes(5)
            waitingQueuePort.enqueueIfAbsent(userId = 1L, now = now)
            waitingQueuePort.admitNext(
                candidates = listOf(AdmissionTokenCandidate(token, now, now.plus(ttl))),
                tokenTtl = ttl,
                now = now,
            )
            waitingQueuePort.enqueueIfAbsent(userId = 2L, now = now)

            val admittedWithCollision = waitingQueuePort.admitNext(
                candidates = listOf(AdmissionTokenCandidate(token, now, now.plus(ttl))),
                tokenTtl = ttl,
                now = now,
            )

            assertThat(admittedWithCollision).isEmpty()
            assertThat(waitingQueuePort.findPosition(2L)).isEqualTo(1L)
            assertThat(waitingQueuePort.validateToken(1L, token, "first-user-key", now).status)
                .isEqualTo(TokenValidationResult.Status.VALID)
        }

        private fun admissionCandidates(
            count: Int,
            now: Instant,
            ttl: Duration,
        ): List<AdmissionTokenCandidate> = (1..count).map { index ->
            AdmissionTokenCandidate(
                token = "batch-token-$index",
                availableAt = now,
                expiresAt = now.plus(ttl),
            )
        }
    }
