package com.loopers.domain.queue

import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = [
        "queue.activate.initial-delay=1h",
        "queue.activate.interval=1s",
        "queue.activate.batch-size=100",
    ],
)
class WaitingQueueServiceIntegrationTest @Autowired constructor(
    private val waitingQueueService: WaitingQueueService,
    private val entryTokenRepository: EntryTokenRepository,
    private val clock: MutableClock,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
        clock.reset()
    }

    @Test
    @DisplayName("대기열에 진입하면 입장 순서대로 1부터 시작하는 순번이 부여된다")
    fun enterAssignsPositionInOrder() {
        val first = waitingQueueService.enter("user-1")
        clock.advanceMillis(1)
        val second = waitingQueueService.enter("user-2")

        assertThat(first).isEqualTo(QueueStatus.Waiting(position = 1, totalWaiting = 1, estimatedWaitSeconds = 1))
        assertThat(second).isEqualTo(QueueStatus.Waiting(position = 2, totalWaiting = 2, estimatedWaitSeconds = 1))
    }

    @Test
    @DisplayName("재진입해도 기존 순번이 유지된다")
    fun reEnterKeepsExistingPosition() {
        waitingQueueService.enter("user-1")
        clock.advanceMillis(1)
        waitingQueueService.enter("user-2")

        val reEntered = waitingQueueService.enter("user-1")

        assertThat(reEntered).isEqualTo(QueueStatus.Waiting(position = 1, totalWaiting = 2, estimatedWaitSeconds = 1))
    }

    @Test
    @DisplayName("활성화되면 앞사람부터 Ready(토큰)가 되고 대기열에서 빠지며, 남은 사람 순번이 당겨진다")
    fun activateNextIssuesTokensInOrder() {
        waitingQueueService.enter("user-1")
        clock.advanceMillis(1)
        waitingQueueService.enter("user-2")
        clock.advanceMillis(1)
        waitingQueueService.enter("user-3")

        waitingQueueService.activateNext(2)

        assertThat(waitingQueueService.status("user-1")).isInstanceOf(QueueStatus.Ready::class.java)
        assertThat(waitingQueueService.status("user-2")).isInstanceOf(QueueStatus.Ready::class.java)
        assertThat(waitingQueueService.status("user-3"))
            .isEqualTo(QueueStatus.Waiting(position = 1, totalWaiting = 1, estimatedWaitSeconds = 1))
    }

    @Test
    @DisplayName("토큰 보유자가 재진입하면 줄을 서지 않고 기존 토큰의 Ready 를 받는다")
    fun readyUserReEnterReturnsSameToken() {
        waitingQueueService.enter("user-1")
        waitingQueueService.activateNext(1)
        val issued = waitingQueueService.status("user-1") as QueueStatus.Ready

        val reEntered = waitingQueueService.enter("user-1")

        assertThat(reEntered).isEqualTo(QueueStatus.Ready(issued.token))
    }

    @Test
    @DisplayName("대기열에 없고 토큰도 없는 유저는 NotInQueue 다")
    fun unknownUserIsNotInQueue() {
        assertThat(waitingQueueService.status("stranger")).isEqualTo(QueueStatus.NotInQueue)
    }

    @Test
    @DisplayName("대기 인원보다 큰 수로 활성화해도 있는 만큼만 처리된다")
    fun activateMoreThanWaitingIsSafe() {
        waitingQueueService.enter("user-1")

        waitingQueueService.activateNext(10)

        assertThat(waitingQueueService.status("user-1")).isInstanceOf(QueueStatus.Ready::class.java)
    }

    @Test
    @DisplayName("[검증] 순번은 이름 사전순이 아니라 진입 시각 순서로 정해진다")
    fun orderFollowsArrivalTimeNotName() {
        listOf("zoe", "amy", "bob").forEach {
            waitingQueueService.enter(it)
            clock.advanceMillis(10)
        }

        assertThat((waitingQueueService.status("zoe") as QueueStatus.Waiting).position).isEqualTo(1)
        assertThat((waitingQueueService.status("amy") as QueueStatus.Waiting).position).isEqualTo(2)
        assertThat((waitingQueueService.status("bob") as QueueStatus.Waiting).position).isEqualTo(3)
    }

    @Test
    @DisplayName("[검증] 동시에 대량 진입해도 유실/중복 없이 전원이 1..N 의 고유 순번을 부여받는다")
    fun concurrentEnterKeepsIntegrity() {
        val userCount = 300
        // 고정 풀(< userCount)이면 워커가 startGun 배리어에 묶여 나머지 태스크를 굶겨 데드락 → 가상 스레드로.
        val pool = Executors.newVirtualThreadPerTaskExecutor()
        val ready = CountDownLatch(userCount)
        val startGun = CountDownLatch(1)
        try {
            (1..userCount).forEach { i ->
                pool.submit {
                    ready.countDown()
                    startGun.await()
                    waitingQueueService.enter("user-$i")
                }
            }
            ready.await()
            startGun.countDown() // 전원 동시 출발
            pool.shutdown()
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        } finally {
            pool.shutdownNow()
        }

        val positions = (1..userCount)
            .map { (waitingQueueService.status("user-$it") as QueueStatus.Waiting).position }
        assertThat(positions.toSet()).isEqualTo((1L..userCount.toLong()).toSet())
    }

    @Test
    @DisplayName("[검증] 입장 토큰은 TTL 이 지나면 자동으로 무효화된다")
    fun tokenExpiresAfterTtl() {
        entryTokenRepository.issue("user-1", "token-1", Duration.ofSeconds(1))
        assertThat(entryTokenRepository.find("user-1")).isEqualTo("token-1")

        Thread.sleep(1_500) // 실제 Redis TTL 만료를 기다린다

        assertThat(entryTokenRepository.find("user-1")).isNull()
    }

    @Test
    @DisplayName("[검증] 배치 크기를 초과해 몰려도 한 틱엔 배치 크기만큼만 입장하고 나머지는 안전하게 대기한다")
    fun activationIsStableUnderOverload() {
        val users = (1..12).map { "user-$it" }
        users.forEach {
            waitingQueueService.enter(it)
            clock.advanceMillis(1)
        }

        waitingQueueService.activateNext(5)
        assertThat(users.count { waitingQueueService.status(it) is QueueStatus.Ready }).isEqualTo(5)
        assertThat(users.count { waitingQueueService.status(it) is QueueStatus.Waiting }).isEqualTo(7)

        waitingQueueService.activateNext(5)
        assertThat(users.count { waitingQueueService.status(it) is QueueStatus.Ready }).isEqualTo(10)
        assertThat(users.count { waitingQueueService.status(it) is QueueStatus.Waiting }).isEqualTo(2)

        waitingQueueService.activateNext(5)
        assertThat(users.count { waitingQueueService.status(it) is QueueStatus.Ready }).isEqualTo(12)
        assertThat(users.count { waitingQueueService.status(it) is QueueStatus.Waiting }).isEqualTo(0)
    }

    @TestConfiguration
    class TestClockConfig {
        @Bean
        @Primary
        fun mutableClock(): MutableClock = MutableClock()
    }

    /**
     * 테스트가 진입 시각을 직접 통제해 순번(ZSET score)을 결정적으로 만들기 위한 Clock.
     * Clock 을 서비스에 주입해둔 설계 덕분에 시간을 고정/전진시킬 수 있다.
     */
    class MutableClock : Clock() {
        private val base = Instant.parse("2026-01-01T00:00:00Z")
        private var current = base

        fun advanceMillis(ms: Long) {
            current = current.plusMillis(ms)
        }

        fun reset() {
            current = base
        }

        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
}
