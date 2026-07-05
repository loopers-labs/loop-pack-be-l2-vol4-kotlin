package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueuePosition

class QueueV1Dto {
    data class EnterResponse(val position: Long)

    data class PositionResponse(
        val position: Long?,
        val waiting: Boolean,
        val estimatedWaitSeconds: Long?,
        val token: String?,
    ) {
        companion object {
            fun from(p: QueuePosition) = PositionResponse(p.position, p.waiting, p.estimatedWaitSeconds, p.token)
        }
    }
}
