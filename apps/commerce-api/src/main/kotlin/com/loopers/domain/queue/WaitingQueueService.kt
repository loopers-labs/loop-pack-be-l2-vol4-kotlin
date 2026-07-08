package com.loopers.domain.queue

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class WaitingQueueService(
    private val waitingQueueRepository: WaitingQueueRepository,
) {
    fun enter(userId: Long): WaitingQueuePosition {
        waitingQueueRepository.enter(userId, ZonedDateTime.now())

        val rank = waitingQueueRepository.findRank(userId)
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "대기열에 존재하지 않는 사용자입니다. userId=$userId")

        return WaitingQueuePosition(
            rank = rank + 1,
            totalCount = waitingQueueRepository.size(),
        )
    }

    fun getPosition(userId: Long): WaitingQueuePosition {
        val rank = waitingQueueRepository.findRank(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "대기열에 존재하지 않는 사용자입니다. userId=$userId")

        return WaitingQueuePosition(
            rank = rank + 1,
            totalCount = waitingQueueRepository.size(),
        )
    }
}
