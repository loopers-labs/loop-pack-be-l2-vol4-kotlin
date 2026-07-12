package com.loopers.interfaces.api.waitingqueue.dto

import com.loopers.domain.waitingqueue.WaitingQueuePosition
import com.loopers.domain.waitingqueue.WaitingQueueStatus

class WaitingQueueV1Dto {
    data class PositionResponse(
        val status: WaitingQueueStatus,
        val rank: Long?,
        val currentTotalWaitingCount: Long,
        val estimatedWaitSeconds: Long?,
        val pollingIntervalSeconds: Long,
        val entryToken: String?,
    ) {
        companion object {
            fun from(position: WaitingQueuePosition): PositionResponse {
                return PositionResponse(
                    status = position.status,
                    rank = position.rank,
                    currentTotalWaitingCount = position.currentTotalWaitingCount,
                    estimatedWaitSeconds = position.estimatedWaitSeconds,
                    pollingIntervalSeconds = position.pollingIntervalSeconds,
                    entryToken = position.entryToken,
                )
            }
        }
    }
}
