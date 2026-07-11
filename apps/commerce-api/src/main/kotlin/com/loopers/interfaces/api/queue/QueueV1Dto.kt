package com.loopers.interfaces.api.queue

import com.loopers.domain.queue.QueueStatus

class QueueV1Dto {
    data class QueueStatusResponse(
        val status: Status,
        val position: Long?,
        val totalWaiting: Long?,
        val estimatedWaitSeconds: Long?,
        val token: String?
    ) {
        enum class Status { WAITING, READY, NOT_IN_QUEUE }

        companion object {
            fun from(domain: QueueStatus): QueueStatusResponse = when (domain) {
                is QueueStatus.Waiting -> QueueStatusResponse(status = Status.WAITING, position = domain.position, totalWaiting = domain.totalWaiting, estimatedWaitSeconds = domain.estimatedWaitSeconds, token = null)
                is QueueStatus.Ready -> QueueStatusResponse(status = Status.READY, position = null, totalWaiting = null, estimatedWaitSeconds = null, token = domain.token)
                is QueueStatus.NotInQueue -> QueueStatusResponse(status = Status.NOT_IN_QUEUE, position = null, totalWaiting = null, estimatedWaitSeconds = null, token = null)
            }
        }
    }
}
