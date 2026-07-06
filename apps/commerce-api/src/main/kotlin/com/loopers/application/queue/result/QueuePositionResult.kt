package com.loopers.application.queue.result

/**
 * 대기열 순번 조회 결과.
 * @param position 0-based 순번. 대기열에 없으면 null(미진입 또는 입장 완료).
 * @param totalWaiting 전체 대기 인원.
 */
data class QueuePositionResult(
    val position: Long?,
    val totalWaiting: Long,
)
