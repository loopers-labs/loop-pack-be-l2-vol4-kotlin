package com.loopers.domain.queue

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class WaitingQueueServiceIntegrationTest @Autowired constructor(
    private val waitingQueueService: WaitingQueueService,
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenRepository: EntryTokenRepository,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("대기열에 진입할 때,")
    @Nested
    inner class Enter {
        @DisplayName("첫 진입자는 1번 순번과 전체 대기 인원 1명을 반환한다.")
        @Test
        fun returnsFirstRank_whenFirstUserEnters() {
            // arrange
            val userId = 1L

            // act
            val position = waitingQueueService.enter(userId)

            // assert (기본 배치 100 / 주기 1초 → rank 1 의 예상 대기 시간은 1초)
            assertAll(
                { assertThat(position.rank).isEqualTo(1L) },
                { assertThat(position.totalCount).isEqualTo(1L) },
                { assertThat(position.estimatedWaitSeconds).isEqualTo(1L) },
            )
        }

        @DisplayName("이미 대기열에 있는 유저가 다시 진입해도 대기 인원은 중복 집계되지 않는다.")
        @Test
        fun doesNotInflateCount_whenUserEntersAgain() {
            // arrange
            waitingQueueService.enter(1L)
            waitingQueueService.enter(2L)

            // act
            val position = waitingQueueService.enter(1L)

            // assert
            assertThat(position.totalCount).isEqualTo(2L)
        }

        @DisplayName("이미 입장 토큰이 발급된 유저가 다시 진입하면 재진입 없이 토큰(READY)을 그대로 반환한다.")
        @Test
        fun returnsToken_whenAdmittedUserReenters() {
            // arrange
            val token = EntryToken.issue()
            entryTokenRepository.save(1L, token)

            // act
            val position = waitingQueueService.enter(1L)

            // assert
            assertAll(
                { assertThat(position.token).isEqualTo(token) },
                { assertThat(position.rank).isNull() },
                // 대기열에 다시 들어가지 않는다
                { assertThat(waitingQueueRepository.findRank(1L)).isNull() },
                { assertThat(waitingQueueRepository.size()).isEqualTo(0L) },
            )
        }

        @DisplayName("여러 유저가 동시에 진입해도 유실 없이 서로 다른 순번을 받는다.")
        @Test
        fun assignsDistinctRanks_whenUsersEnterConcurrently() {
            // arrange
            val userCount = 50

            // act
            val results = runConcurrently(userCount) { index ->
                waitingQueueService.enter(index.toLong() + 1)
            }

            // assert
            val ranks = results.map { it.getOrThrow().rank }
            assertAll(
                { assertThat(results).allSatisfy { assertThat(it.isSuccess).isTrue() } },
                { assertThat(ranks.toSet()).hasSize(userCount) },
                { assertThat(ranks).containsExactlyInAnyOrderElementsOf((1..userCount).map { it.toLong() }) },
                { assertThat(waitingQueueService.enter(9999L).totalCount).isEqualTo(userCount + 1L) },
            )
        }
    }

    @DisplayName("대기열 순번을 조회할 때,")
    @Nested
    inner class GetPosition {
        @DisplayName("아직 입장하지 않은 대기 유저는 순번과 전체 인원을 반환하고 토큰은 없다.")
        @Test
        fun returnsRankWithoutToken_whenStillWaiting() {
            // arrange
            waitingQueueService.enter(1L)
            waitingQueueService.enter(2L)

            // act
            val position = waitingQueueService.getPosition(1L)

            // assert
            assertAll(
                { assertThat(position.rank).isEqualTo(1L) },
                { assertThat(position.totalCount).isEqualTo(2L) },
                { assertThat(position.token).isNull() },
            )
        }

        @DisplayName("입장 토큰이 발급된 유저는 순번 대신 토큰을 반환한다.")
        @Test
        fun returnsToken_whenAdmitted() {
            // arrange
            val token = EntryToken.issue()
            entryTokenRepository.save(1L, token)

            // act
            val position = waitingQueueService.getPosition(1L)

            // assert
            assertAll(
                { assertThat(position.token).isEqualTo(token) },
                { assertThat(position.rank).isNull() },
                { assertThat(position.totalCount).isNull() },
            )
        }

        @DisplayName("대기열에 없는 유저가 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserNotInQueue() {
            // act
            val exception = assertThrows<CoreException> {
                waitingQueueService.getPosition(999L)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    private fun <T> runConcurrently(
        times: Int,
        task: (Int) -> T,
    ): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(times)
        val ready = CountDownLatch(times)
        val start = CountDownLatch(1)

        return try {
            val futures = (0 until times).map { index ->
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching { task(index) }
                    },
                )
            }
            ready.await()
            start.countDown()
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }
}
