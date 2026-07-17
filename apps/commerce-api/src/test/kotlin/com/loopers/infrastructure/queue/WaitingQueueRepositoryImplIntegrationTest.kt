package com.loopers.infrastructure.queue

import com.loopers.domain.queue.WaitingQueueRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class WaitingQueueRepositoryImplIntegrationTest @Autowired constructor(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("대기열에 진입할 때,")
    @Nested
    inner class Enter {
        @DisplayName("이미 대기열에 있는 유저가 더 늦은 시각에 다시 진입하면 맨 뒤로 재배치된다.")
        @Test
        fun movesToBack_whenReenteredWithLaterTimestamp() {
            // arrange
            val base = ZonedDateTime.now()
            waitingQueueRepository.enter(1L, base)
            waitingQueueRepository.enter(2L, base.plusSeconds(1))

            // act
            waitingQueueRepository.enter(1L, base.plusSeconds(2))

            // assert
            assertAll(
                { assertThat(waitingQueueRepository.findRank(2L)).isEqualTo(0L) },
                { assertThat(waitingQueueRepository.findRank(1L)).isEqualTo(1L) },
                { assertThat(waitingQueueRepository.size()).isEqualTo(2L) },
            )
        }

        @DisplayName("진입 시각이 동일하면 userId(member) 사전순으로 순번이 정해진다.")
        @Test
        fun ordersByMemberLexicographically_whenTimestampsAreTied() {
            // arrange : 호출 순서는 2 -> 1 이지만 진입 시각(score)이 동일하다
            val sameInstant = ZonedDateTime.now()

            // act
            waitingQueueRepository.enter(2L, sameInstant)
            waitingQueueRepository.enter(1L, sameInstant)

            // assert : score 가 같으면 Redis 는 member 문자열 사전순("1" < "2")으로 정렬한다
            assertAll(
                { assertThat(waitingQueueRepository.findRank(1L)).isEqualTo(0L) },
                { assertThat(waitingQueueRepository.findRank(2L)).isEqualTo(1L) },
            )
        }
    }
}
