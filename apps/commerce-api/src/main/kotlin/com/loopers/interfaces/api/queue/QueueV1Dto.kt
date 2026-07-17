package com.loopers.interfaces.api.queue

import com.loopers.domain.queue.QueuePositionInfo

/**
 * 대기열 API DTO.
 */
class QueueV1Dto {

    /**
     * 대기열 순번 조회 응답.
     *
     * @property position 현재 순번 (1-based). 0이면 입장 가능 상태.
     * @property totalWaiting 전체 대기 인원
     * @property estimatedWaitSeconds 예상 대기 시간 (초)
     * @property token 입장 토큰 (발급된 경우에만 존재)
     */
    data class QueuePositionResponse(
        val position: Long,
        val totalWaiting: Long,
        val estimatedWaitSeconds: Long,
        val token: String?,
    ) {
        companion object {
            /** QueuePositionInfo에서 변환 */
            fun from(info: QueuePositionInfo) = QueuePositionResponse(
                position = info.position,
                totalWaiting = info.totalWaiting,
                estimatedWaitSeconds = info.estimatedWaitSeconds,
                token = info.token,
            )
        }
    }
}
