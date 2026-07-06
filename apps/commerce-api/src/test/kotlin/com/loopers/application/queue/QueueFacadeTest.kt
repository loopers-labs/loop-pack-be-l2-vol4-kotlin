package com.loopers.application.queue

import com.loopers.application.queue.port.WaitingQueueRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("QueueFacade")
class QueueFacadeTest {
    private val waitingQueueRepository: WaitingQueueRepository = mockk(relaxed = true)
    private val queueFacade = QueueFacade(waitingQueueRepository)

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
}
