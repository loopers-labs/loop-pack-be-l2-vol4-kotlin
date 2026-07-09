package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueueInfo
import com.loopers.application.queue.QueueStatus

class QueueV1Dto {
    /**
     * @property status WAITING(대기) / READY(입장 완료).
     * @property position 1-based 순번("당신은 N번째"). WAITING 일 때만. 도메인의 0-based rank + 1.
     * @property totalWaiting 전체 대기 인원.
     * @property estimatedWaitSeconds 예상 대기 시간(초). WAITING 일 때만.
     * @property token 입장 토큰. READY 일 때만.
     */
    data class QueueResponse(
        val status: QueueStatus,
        val position: Long?,
        val totalWaiting: Long,
        val estimatedWaitSeconds: Long?,
        val token: String?,
    ) {
        companion object {
            fun from(info: QueueInfo) = QueueResponse(
                status = info.status,
                position = info.rank?.let { it + 1 },
                totalWaiting = info.totalWaiting,
                estimatedWaitSeconds = info.estimatedWaitSeconds,
                token = info.token,
            )
        }
    }
}
