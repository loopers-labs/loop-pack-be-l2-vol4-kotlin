package com.loopers.application.queue

import com.loopers.domain.queue.QueuePosition

class WaitingQueueInfo {
    data class PositionView(
        val rank: Long?,
        val totalCount: Long?,
        val estimatedWaitSeconds: Long?,
        val token: String?,
    ) {
        companion object {
            fun from(position: QueuePosition): PositionView =
                PositionView(
                    rank = position.rank,
                    totalCount = position.totalCount,
                    estimatedWaitSeconds = position.estimatedWaitSeconds,
                    token = position.token?.value,
                )
        }
    }
}
