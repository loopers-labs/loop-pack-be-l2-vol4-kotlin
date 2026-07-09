package com.loopers.interfaces.api.waitingqueue

import com.loopers.application.waitingqueue.WaitTokenResult

class WaitingQueueV1Dto {
    /** 대기열 등록(429) 시 내려주는 바디. 클라이언트는 waitToken 으로 순번을 조회한다. */
    data class EnterResponse(
        val topic: String,
        val waitToken: String,
        val message: String,
    ) {
        companion object {
            fun from(result: WaitTokenResult): EnterResponse = EnterResponse(
                topic = result.topic,
                waitToken = result.waitToken,
                message = "대기열에 등록되었습니다. GET /api/v1/queue/position 으로 순번을 확인하세요.",
            )
        }
    }
}
