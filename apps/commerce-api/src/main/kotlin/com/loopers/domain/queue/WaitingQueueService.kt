package com.loopers.domain.queue

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Component
class WaitingQueueService(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenRepository: EntryTokenRepository,
    private val clock: Clock
) {
    fun enter(loginId: String): QueueStatus {
        entryTokenRepository.find(loginId)?.let { return QueueStatus.Ready(it) }
        waitingQueueRepository.addIfAbsent(loginId, clock.millis())
        return status(loginId)
    }

    fun status(loginId: String): QueueStatus {
        entryTokenRepository.find(loginId)?.let { return QueueStatus.Ready(it) }
        val rank = waitingQueueRepository.rank(loginId) ?: return QueueStatus.NotInQueue
        return QueueStatus.Waiting(position = rank + 1, totalWaiting = waitingQueueRepository.size())
    }

    fun activateNext(count: Int) {
        val nextInLine = waitingQueueRepository.peekNext(count)
        if (nextInLine.isEmpty()) return

        nextInLine.forEach { loginId ->
            entryTokenRepository.issue(loginId, UUID.randomUUID().toString(), TOKEN_TTL)
        }
        waitingQueueRepository.remove(nextInLine)
    }

    companion object {
        private val TOKEN_TTL: Duration = Duration.ofMinutes(10)
    }
}
