package com.loopers.domain.waitingqueue.unit

import com.loopers.domain.waitingqueue.scheduler.WaitingQueueScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled

class WaitingQueueSchedulerTest {
    @Test
    fun `입장_스케줄러는_PT10S_literal_default를_직접_갖지_않고_중앙_설정만_참조한다`() {
        val scheduled = WaitingQueueScheduler::class.java
            .getDeclaredMethod("admitBatch")
            .getAnnotation(Scheduled::class.java)

        assertThat(scheduled.fixedDelayString).doesNotContain("PT10S")
        assertThat(scheduled.initialDelayString).doesNotContain("PT10S")
        assertThat(scheduled.fixedDelayString).doesNotContain(":")
        assertThat(scheduled.initialDelayString).doesNotContain(":")
        assertThat(scheduled.fixedDelayString).contains("commerce.waiting-queue.scheduler-delay")
        assertThat(scheduled.initialDelayString).contains("commerce.waiting-queue.scheduler-delay")
    }
}
