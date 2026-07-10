package com.loopers.domain.queue

import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
class WaitingQueueRepositoryIntegrationTest @Autowired constructor(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("대기열에 진입하면, 첫 진입자의 순번은 0이고 전체 인원은 1이다.")
    @Test
    fun enter_firstEntrant() {
        // arrange
        val userId = 1L

        // act
        waitingQueueRepository.enter(userId, Instant.parse("2026-01-01T00:00:00Z"))

        // assert
        assertThat(waitingQueueRepository.position(userId)).isEqualTo(0L)
        assertThat(waitingQueueRepository.size()).isEqualTo(1L)
    }

    @DisplayName("먼저 진입한 유저가 앞 순번을 가진다. (score = 진입 시각)")
    @Test
    fun order_byEntryTime() {
        // act
        waitingQueueRepository.enter(1L, Instant.parse("2026-01-01T00:00:00Z"))
        waitingQueueRepository.enter(2L, Instant.parse("2026-01-01T00:00:01Z"))

        // assert
        assertThat(waitingQueueRepository.position(1L)).isEqualTo(0L)
        assertThat(waitingQueueRepository.position(2L)).isEqualTo(1L)
    }

    @DisplayName("이미 대기 중인 유저가 재진입해도 최초 순번이 유지되고 인원은 늘지 않는다. (ZADD NX)")
    @Test
    fun reEnter_keepsOriginalPosition() {
        // arrange
        waitingQueueRepository.enter(1L, Instant.parse("2026-01-01T00:00:00Z"))
        waitingQueueRepository.enter(2L, Instant.parse("2026-01-01T00:00:01Z"))

        // act : 1번 유저가 더 늦은 시각으로 재진입을 시도한다
        waitingQueueRepository.enter(1L, Instant.parse("2026-01-01T00:00:02Z"))

        // assert : 뒤로 밀리지 않고 여전히 0번, 인원도 그대로 2명
        assertThat(waitingQueueRepository.position(1L)).isEqualTo(0L)
        assertThat(waitingQueueRepository.size()).isEqualTo(2L)
    }

    @DisplayName("대기열에 없는 유저의 순번을 조회하면 null 을 반환한다.")
    @Test
    fun position_whenNotEntered_returnsNull() {
        // assert
        assertThat(waitingQueueRepository.position(999L)).isNull()
    }
}
