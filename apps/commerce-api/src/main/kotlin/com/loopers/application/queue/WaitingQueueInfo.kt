package com.loopers.application.queue

import com.loopers.domain.queue.WaitingQueuePosition

class WaitingQueueInfo {
    data class Position(
        val rank: Long,
        val totalCount: Long,
    ) {
        companion object {
            fun from(position: WaitingQueuePosition): Position =
                Position(
                    rank = position.rank,
                    totalCount = position.totalCount,
                )
        }
    }
}
