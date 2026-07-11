package com.loopers.domain.queue

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlin.math.ceil

@Component
class WaitingQueueService(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenRepository: EntryTokenRepository,
    private val clock: Clock,
    @Value("\${queue.activate.batch-size}")
    private val batchSize: Int,
    @Value("\${queue.activate.interval}")
    private val activateInterval: Duration,
    @Value("\${queue.token.ttl}")
    private val tokenTtl: Duration,
) {
    fun enter(loginId: String): QueueStatus {
        entryTokenRepository.find(loginId)?.let { return QueueStatus.Ready(it) }
        waitingQueueRepository.addIfAbsent(loginId, clock.millis())
        return status(loginId)
    }

    fun status(loginId: String): QueueStatus {
        entryTokenRepository.find(loginId)?.let { return QueueStatus.Ready(it) }
        val rank = waitingQueueRepository.rank(loginId) ?: return QueueStatus.NotInQueue
        val position = rank + 1
        return QueueStatus.Waiting(
            position = position,
            totalWaiting = waitingQueueRepository.size(),
            estimatedWaitSeconds = estimateWaitSeconds(position),
        )
    }

    fun activateNext(count: Int) {
        val nextInLine = waitingQueueRepository.peekNext(count)
        if (nextInLine.isEmpty()) return

        nextInLine.forEach { loginId ->
            entryTokenRepository.issue(loginId, UUID.randomUUID().toString(), tokenTtl)
        }
        waitingQueueRepository.remove(nextInLine)
    }

    /**
     * 내 앞의 배치가 모두 빠질 때까지 걸리는 예상 시간(초).
     * 처리량 = batchSize / interval 이므로, 내가 속한 배치 순번 * interval 이 예상 대기다.
     */
    private fun estimateWaitSeconds(position: Long): Long {
        val batchesAhead = ceil(position.toDouble() / batchSize).toLong()
        return (batchesAhead * activateInterval.toMillis()) / 1000
    }
}
