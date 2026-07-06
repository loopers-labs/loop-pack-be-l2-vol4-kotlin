package com.loopers.interfaces.api.queue

import com.loopers.application.queue.result.QueuePositionResult

class QueueV1Dto {
    data class PositionResponse(
        val position: Long?,
        val totalWaiting: Long,
        // 예상 대기 시간(초). 사용자에게는 "약 N분"으로 표기한다.
        val estimatedWaitSeconds: Long,
        // 입장 토큰. 발급됐으면 값, 대기 중이면 null. 이 값이 채워지면 주문 API 를 호출할 차례다.
        val entryToken: String?,
    ) {
        companion object {
            fun from(result: QueuePositionResult): PositionResponse = PositionResponse(
                position = result.position,
                totalWaiting = result.totalWaiting,
                estimatedWaitSeconds = result.estimatedWaitSeconds,
                entryToken = result.entryToken,
            )
        }
    }
}
