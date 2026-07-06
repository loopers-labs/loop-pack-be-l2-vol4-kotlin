package com.loopers.application.queue

import com.loopers.application.queue.port.EntryTokenStore
import com.loopers.application.queue.port.WaitingQueueRepository
import com.loopers.domain.queue.EntryToken
import com.loopers.domain.queue.QueueErrorType
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

@DisplayName("QueueFacade")
class QueueFacadeTest {
    private val waitingQueueRepository: WaitingQueueRepository = mockk(relaxed = true)
    private val entryTokenStore: EntryTokenStore = mockk(relaxed = true)
    private val queueFacade = QueueFacade(waitingQueueRepository, entryTokenStore, TOKEN_TTL_SECONDS)

    @Test
    @DisplayName("enter — 대기열에 진입시키고 순번·전체 인원을 반환한다")
    fun enterReturnsPosition() {
        every { waitingQueueRepository.enter(eq(7L), any()) } returns true
        every { waitingQueueRepository.rank(7L) } returns 2L
        every { waitingQueueRepository.size() } returns 3L

        val result = queueFacade.enter(7L)

        verify { waitingQueueRepository.enter(eq(7L), any<Instant>()) }
        assertThat(result.position).isEqualTo(2L)
        assertThat(result.totalWaiting).isEqualTo(3L)
    }

    @Test
    @DisplayName("position — 대기열에 없으면 position 은 null, 전체 인원만 반환한다")
    fun positionIsNullWhenAbsent() {
        every { waitingQueueRepository.rank(404L) } returns null
        every { waitingQueueRepository.size() } returns 5L

        val result = queueFacade.position(404L)

        assertThat(result.position).isNull()
        assertThat(result.totalWaiting).isEqualTo(5L)
    }

    @Test
    @DisplayName("admit — 꺼낸 인원 각각에 TTL 토큰을 발급하고 발급 수를 반환한다")
    fun admitIssuesTokens() {
        every { waitingQueueRepository.pollNext(3L) } returns listOf(1L, 2L, 3L)

        val issued = queueFacade.admit(3)

        assertThat(issued).isEqualTo(3)
        verify(exactly = 1) { entryTokenStore.issue(eq(1L), any(), Duration.ofSeconds(TOKEN_TTL_SECONDS)) }
        verify(exactly = 1) { entryTokenStore.issue(eq(2L), any(), Duration.ofSeconds(TOKEN_TTL_SECONDS)) }
        verify(exactly = 1) { entryTokenStore.issue(eq(3L), any(), Duration.ofSeconds(TOKEN_TTL_SECONDS)) }
    }

    @Test
    @DisplayName("admit — 대기열이 비어 있으면 발급하지 않는다")
    fun admitIssuesNothingWhenEmpty() {
        every { waitingQueueRepository.pollNext(any()) } returns emptyList()

        val issued = queueFacade.admit(18)

        assertThat(issued).isEqualTo(0)
        verify(exactly = 0) { entryTokenStore.issue(any(), any(), any()) }
    }

    @Test
    @DisplayName("ensureAdmitted — 저장된 토큰과 값이 같으면 통과한다")
    fun ensureAdmittedPasses() {
        every { entryTokenStore.find(7L) } returns EntryToken("token-abc")

        queueFacade.ensureAdmitted(7L, "token-abc")
    }

    @Test
    @DisplayName("ensureAdmitted — 토큰이 없으면 ENTRY_TOKEN_INVALID")
    fun ensureAdmittedThrowsWhenMissing() {
        every { entryTokenStore.find(7L) } returns null

        val ex = assertThrows<CoreException> { queueFacade.ensureAdmitted(7L, "token-abc") }
        assertThat(ex.errorType).isEqualTo(QueueErrorType.ENTRY_TOKEN_INVALID)
    }

    @Test
    @DisplayName("ensureAdmitted — 토큰 값이 다르면 ENTRY_TOKEN_INVALID")
    fun ensureAdmittedThrowsWhenMismatch() {
        every { entryTokenStore.find(7L) } returns EntryToken("stored")

        val ex = assertThrows<CoreException> { queueFacade.ensureAdmitted(7L, "given") }
        assertThat(ex.errorType).isEqualTo(QueueErrorType.ENTRY_TOKEN_INVALID)
    }

    @Test
    @DisplayName("leave — 토큰을 회수한다")
    fun leaveRemovesToken() {
        queueFacade.leave(7L)

        verify { entryTokenStore.remove(7L) }
    }

    companion object {
        private const val TOKEN_TTL_SECONDS = 300L
    }
}
