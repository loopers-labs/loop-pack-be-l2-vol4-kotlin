package com.loopers.interfaces.api.queue

import com.loopers.application.queue.WaitingQueueInfo

class WaitingQueueV1Dto {
    data class EnterResponse(
        val rank: Long,
        val totalCount: Long,
    ) {
        companion object {
            fun from(info: WaitingQueueInfo.Position): EnterResponse =
                EnterResponse(
                    rank = info.rank,
                    totalCount = info.totalCount,
                )
        }
    }

    data class PositionResponse(
        val rank: Long,
        val totalCount: Long,
    ) {
        companion object {
            fun from(info: WaitingQueueInfo.Position): PositionResponse =
                PositionResponse(
                    rank = info.rank,
                    totalCount = info.totalCount,
                )
        }
    }
}
