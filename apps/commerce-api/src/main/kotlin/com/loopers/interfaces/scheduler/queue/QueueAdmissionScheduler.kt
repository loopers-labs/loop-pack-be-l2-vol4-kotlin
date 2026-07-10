package com.loopers.interfaces.scheduler.queue

import com.loopers.domain.queue.EntryTokenService
import com.loopers.domain.queue.WaitingQueueService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 대기열에서 유저를 순차 입장시키는 스케줄러.
 *
 * 매 tick 마다 `targetActive − 현재 활성` 만큼만 발급하는 리키버킷 방식으로 하류 처리량을 평탄화한다.
 * ZPOPMIN 으로 꺼낸 직후 토큰을 발급한다(pop-then-issue). 발급 전 장애로 유실이 나면
 * 해당 유저는 큐에서 빠지지만, polling 에서 토큰 부재를 확인해 재진입(ZADD NX)으로 복구한다.
 */
@Component
class QueueAdmissionScheduler(
    private val waitingQueueService: WaitingQueueService,
    private val entryTokenService: EntryTokenService,
    @Value("\${queue.admission.target-active:20}") private val targetActive: Long,
    @Value("\${queue.admission.enabled:true}") private val enabled: Boolean,
) {
    @Scheduled(
        fixedDelayString = "\${queue.admission.tick-ms:100}",
        initialDelayString = "\${queue.admission.initial-delay-ms:0}",
    )
    fun admit() {
        if (!enabled) return
        admitOnce()
    }

    /** 한 tick 분의 입장 처리: `targetActive − 현재 활성` 만큼 꺼내 토큰을 발급한다. (테스트에서 직접 호출) */
    fun admitOnce() {
        val capacity = targetActive - entryTokenService.activeCount()
        if (capacity <= 0) return

        waitingQueueService.poll(capacity).forEach { userId ->
            runCatching { entryTokenService.issue(userId) }
                .onFailure { logger.warn("입장 토큰 발급 실패. userId={}", userId, it) }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(QueueAdmissionScheduler::class.java)
    }
}
