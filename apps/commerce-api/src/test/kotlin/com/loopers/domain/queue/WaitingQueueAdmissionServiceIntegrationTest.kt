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
import java.time.ZonedDateTime

@SpringBootTest
class WaitingQueueAdmissionServiceIntegrationTest @Autowired constructor(
    private val waitingQueueAdmissionService: WaitingQueueAdmissionService,
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenRepository: EntryTokenRepository,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("대기열 입장을 처리할 때,")
    @Nested
    inner class Admit {
        @DisplayName("대기열 앞에서 count명을 꺼내 각자에게 토큰을 발급하고 대기열에서 제거한다.")
        @Test
        fun popsFrontUsersAndIssuesTokens() {
            // arrange : 진입 시각을 벌려 pop 순서를 결정론적으로 만든다
            val base = ZonedDateTime.now()
            waitingQueueRepository.enter(1L, base)
            waitingQueueRepository.enter(2L, base.plusSeconds(1))
            waitingQueueRepository.enter(3L, base.plusSeconds(2))

            // act
            val admittedCount = waitingQueueAdmissionService.admit(2)

            // assert
            assertAll(
                { assertThat(admittedCount).isEqualTo(2) },
                // 앞 2명(1, 2)은 토큰 발급 + 대기열에서 제거
                { assertThat(entryTokenRepository.find(1L)).isNotNull() },
                { assertThat(entryTokenRepository.find(2L)).isNotNull() },
                { assertThat(waitingQueueRepository.findRank(1L)).isNull() },
                { assertThat(waitingQueueRepository.findRank(2L)).isNull() },
                // 3번은 아직 대기열에 남아 있고 토큰 없음
                { assertThat(entryTokenRepository.find(3L)).isNull() },
                { assertThat(waitingQueueRepository.findRank(3L)).isEqualTo(0L) },
                { assertThat(waitingQueueRepository.size()).isEqualTo(1L) },
            )
        }

        @DisplayName("대기 인원보다 많은 수를 요청하면 있는 만큼만 입장 처리한다.")
        @Test
        fun admitsOnlyAvailable_whenCountExceedsQueueSize() {
            // arrange
            waitingQueueRepository.enter(1L, ZonedDateTime.now())
            waitingQueueRepository.enter(2L, ZonedDateTime.now().plusSeconds(1))

            // act
            val admittedCount = waitingQueueAdmissionService.admit(5)

            // assert
            assertAll(
                { assertThat(admittedCount).isEqualTo(2) },
                { assertThat(entryTokenRepository.find(1L)).isNotNull() },
                { assertThat(entryTokenRepository.find(2L)).isNotNull() },
                { assertThat(waitingQueueRepository.size()).isEqualTo(0L) },
            )
        }

        @DisplayName("대기열이 비어 있으면 아무도 입장 처리하지 않는다.")
        @Test
        fun admitsNobody_whenQueueEmpty() {
            // act
            val admittedCount = waitingQueueAdmissionService.admit(5)

            // assert
            assertThat(admittedCount).isEqualTo(0)
        }
    }

    @DisplayName("입장 토큰을 검증할 때,")
    @Nested
    inner class Verify {
        @DisplayName("발급된 토큰과 일치하면 예외 없이 통과한다.")
        @Test
        fun passes_whenTokenMatches() {
            // arrange
            val token = EntryToken.issue()
            entryTokenRepository.save(1L, token)

            // act & assert (예외가 발생하지 않아야 한다)
            waitingQueueAdmissionService.verify(1L, token.value)
        }

        @DisplayName("토큰을 제시하지 않으면 FORBIDDEN 예외가 발생한다.")
        @Test
        fun throwsForbidden_whenTokenIsNull() {
            // act
            val exception = assertThrows<CoreException> {
                waitingQueueAdmissionService.verify(1L, null)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.FORBIDDEN)
        }

        @DisplayName("발급된 토큰이 없으면 FORBIDDEN 예외가 발생한다.")
        @Test
        fun throwsForbidden_whenNoIssuedToken() {
            // act
            val exception = assertThrows<CoreException> {
                waitingQueueAdmissionService.verify(1L, "some-token")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.FORBIDDEN)
        }

        @DisplayName("발급된 토큰과 다른 토큰을 제시하면 FORBIDDEN 예외가 발생한다.")
        @Test
        fun throwsForbidden_whenTokenMismatch() {
            // arrange
            entryTokenRepository.save(1L, EntryToken.issue())

            // act
            val exception = assertThrows<CoreException> {
                waitingQueueAdmissionService.verify(1L, "wrong-token")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.FORBIDDEN)
        }
    }

    @DisplayName("입장을 완료 처리할 때,")
    @Nested
    inner class CompleteEntry {
        @DisplayName("발급된 입장 토큰을 삭제한다.")
        @Test
        fun deletesToken() {
            // arrange
            entryTokenRepository.save(1L, EntryToken.issue())

            // act
            waitingQueueAdmissionService.completeEntry(1L)

            // assert
            assertThat(entryTokenRepository.find(1L)).isNull()
        }
    }
}
