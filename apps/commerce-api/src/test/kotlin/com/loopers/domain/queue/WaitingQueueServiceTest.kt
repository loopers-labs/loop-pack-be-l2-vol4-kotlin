package com.loopers.domain.queue

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WaitingQueueServiceTest {
    private val repository = mockk<WaitingQueueRepository>()

    @DisplayName("예상 대기 시간은 (0-based 순번 / 초당 처리량) 초이다.")
    @Test
    fun estimatedWaitSeconds() {
        // arrange : 초당 50명 처리
        val service = WaitingQueueService(repository, throughputPerSecond = 50)

        // act & assert
        assertThat(service.estimatedWaitSeconds(300)).isEqualTo(6L) // 순번 300 → 6초
        assertThat(service.estimatedWaitSeconds(0)).isEqualTo(0L) // 맨 앞은 대기 0
    }
}
