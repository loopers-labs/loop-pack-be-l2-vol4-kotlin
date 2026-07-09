package com.loopers.interfaces.scheduler.queue

import com.loopers.domain.queue.EntryTokenService
import com.loopers.domain.queue.WaitingQueueRepository
import com.loopers.domain.queue.WaitingQueueService
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

/**
 * target-active 를 3 으로 낮추고, 스케줄러 로직(admitOnce)을 직접 호출해 결정적으로 검증한다.
 * (자동 발화 admit() 은 test 프로파일에서 전역 비활성)
 */
@SpringBootTest(
    properties = ["queue.admission.target-active=3"],
)
class QueueAdmissionSchedulerIntegrationTest @Autowired constructor(
    private val queueAdmissionScheduler: QueueAdmissionScheduler,
    private val waitingQueueRepository: WaitingQueueRepository,
    private val waitingQueueService: WaitingQueueService,
    private val entryTokenService: EntryTokenService,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    /** 진입 시각을 1초씩 벌려 순번을 결정적으로 만든다. */
    private fun enterOrdered(vararg userIds: Long) {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        userIds.forEachIndexed { i, userId ->
            waitingQueueRepository.enter(userId, base.plusSeconds(i.toLong()))
        }
    }

    @DisplayName("활성이 없으면 target-active(3) 만큼 입장시키고 큐에서 그만큼 빠진다.")
    @Test
    fun admitsUpToTargetAndDrainsQueue() {
        // arrange : 5명 진입 (target 3 초과)
        enterOrdered(1L, 2L, 3L, 4L, 5L)

        // act
        queueAdmissionScheduler.admitOnce()

        // assert : 앞 3명 입장(토큰 발급), 뒤 2명 잔류
        assertThat(entryTokenService.activeCount()).isEqualTo(3L)
        assertThat(waitingQueueService.size()).isEqualTo(2L)
        assertThat(waitingQueueService.position(1L)).isNull() // 입장해서 큐에서 빠짐
        assertThat(waitingQueueService.position(4L)).isEqualTo(0L) // 남은 유저가 맨 앞으로
    }

    @DisplayName("이미 활성이 target 에 가까우면 부족분만 발급한다. (리키버킷)")
    @Test
    fun admitsOnlyRemainingCapacity() {
        // arrange : 활성 2개 선점 + 큐에 5명
        entryTokenService.issue(101L)
        entryTokenService.issue(102L)
        enterOrdered(1L, 2L, 3L, 4L, 5L)

        // act : capacity = 3 - 2 = 1
        queueAdmissionScheduler.admitOnce()

        // assert
        assertThat(entryTokenService.activeCount()).isEqualTo(3L)
        assertThat(waitingQueueService.size()).isEqualTo(4L)
        assertThat(waitingQueueService.position(1L)).isNull() // 맨 앞 1명만 입장
    }

    @DisplayName("활성이 target 이상이면 아무도 입장하지 못한다.")
    @Test
    fun admitsNoneWhenAtCapacity() {
        // arrange : 활성 3개로 가득 + 큐에 2명
        entryTokenService.issue(101L)
        entryTokenService.issue(102L)
        entryTokenService.issue(103L)
        enterOrdered(1L, 2L)

        // act : capacity = 0
        queueAdmissionScheduler.admitOnce()

        // assert : 큐 그대로
        assertThat(waitingQueueService.size()).isEqualTo(2L)
        assertThat(entryTokenService.activeCount()).isEqualTo(3L)
    }
}
