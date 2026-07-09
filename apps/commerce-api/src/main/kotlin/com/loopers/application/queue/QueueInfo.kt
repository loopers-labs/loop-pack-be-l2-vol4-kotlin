package com.loopers.application.queue

enum class QueueStatus {
    /** 대기 중 — 순번과 예상 대기 시간을 본다. */
    WAITING,

    /** 입장 완료 — 발급된 토큰으로 주문 API 에 진입할 수 있다. */
    READY,
}

/**
 * 대기열 조회 결과.
 *
 * @property rank 0-based 순번(ZRANK). WAITING 일 때만. 표시용 "N번째" 변환은 인터페이스 레이어에서 수행한다.
 * @property totalWaiting 전체 대기 인원(ZCARD).
 * @property estimatedWaitSeconds 예상 대기 시간(초). WAITING 일 때만.
 * @property token 입장 토큰. READY 일 때만.
 */
data class QueueInfo(
    val status: QueueStatus,
    val rank: Long?,
    val totalWaiting: Long,
    val estimatedWaitSeconds: Long?,
    val token: String?,
) {
    companion object {
        fun waiting(rank: Long, totalWaiting: Long, estimatedWaitSeconds: Long) =
            QueueInfo(QueueStatus.WAITING, rank, totalWaiting, estimatedWaitSeconds, token = null)

        fun ready(token: String) =
            QueueInfo(QueueStatus.READY, rank = null, totalWaiting = 0, estimatedWaitSeconds = null, token = token)
    }
}
