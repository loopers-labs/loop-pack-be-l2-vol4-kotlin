package com.loopers.interfaces.api.queue

import com.loopers.application.queue.result.QueuePositionResult

class QueueV1Dto {
    data class PositionResponse(
        val position: Long?,
        val totalWaiting: Long,
    ) {
        companion object {
            fun from(result: QueuePositionResult): PositionResponse = PositionResponse(
                position = result.position,
                totalWaiting = result.totalWaiting,
            )
        }
    }
}
