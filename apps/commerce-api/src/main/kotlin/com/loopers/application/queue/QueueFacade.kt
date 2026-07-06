package com.loopers.application.queue

import com.loopers.application.queue.port.WaitingQueueRepository
import com.loopers.application.queue.result.QueuePositionResult
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class QueueFacade(
    private val waitingQueueRepository: WaitingQueueRepository,
) {
    /** 대기열에 진입하고 현재 순번을 반환한다. 이미 진입한 유저는 기존 순번을 유지한다. */
    fun enter(userId: Long): QueuePositionResult {
        waitingQueueRepository.enter(userId, Instant.now())
        return position(userId)
    }

    /** 현재 순번과 전체 대기 인원을 조회한다. 대기열에 없으면 position 은 null. */
    fun position(userId: Long): QueuePositionResult =
        QueuePositionResult(
            position = waitingQueueRepository.rank(userId),
            totalWaiting = waitingQueueRepository.size(),
        )
}
