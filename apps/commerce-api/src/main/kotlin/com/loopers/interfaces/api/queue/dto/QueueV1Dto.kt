package com.loopers.interfaces.api.queue.dto

import com.loopers.domain.queue.WaitingQueuePosition
import com.loopers.domain.queue.WaitingQueueStatus

class QueueV1Dto {
    data class PositionResponse(
        val status: WaitingQueueStatus,
        val rank: Long?,
        val totalWaiting: Long,
        val estimatedWaitSeconds: Long?,
        val pollingIntervalSeconds: Long,
        val entryToken: String?,
    ) {
        companion object {
            fun from(position: WaitingQueuePosition): PositionResponse {
                return PositionResponse(
                    status = position.status,
                    rank = position.rank,
                    totalWaiting = position.totalWaiting,
                    estimatedWaitSeconds = position.estimatedWaitSeconds,
                    pollingIntervalSeconds = position.pollingIntervalSeconds,
                    entryToken = position.entryToken,
                )
            }
        }
    }
}
