package com.loopers.interfaces.api.queue

import com.loopers.application.queue.WaitingQueueInfo
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class WaitingQueueV1Dto {
    data class PositionResponse(
        val status: Status,
        val rank: Long?,
        val totalCount: Long?,
        val token: String?,
    ) {
        enum class Status {
            WAITING,
            READY,
        }

        companion object {
            fun waiting(rank: Long, totalCount: Long): PositionResponse =
                PositionResponse(
                    status = Status.WAITING,
                    rank = rank,
                    totalCount = totalCount,
                    token = null,
                )

            fun ready(token: String): PositionResponse =
                PositionResponse(
                    status = Status.READY,
                    rank = null,
                    totalCount = null,
                    token = token,
                )

            fun from(info: WaitingQueueInfo.PositionView): PositionResponse =
                info.token?.let { ready(it) }
                    ?: waiting(
                        rank = info.rank
                            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "대기 순번 정보가 없습니다."),
                        totalCount = info.totalCount
                            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "대기 인원 정보가 없습니다."),
                    )
        }
    }
}
