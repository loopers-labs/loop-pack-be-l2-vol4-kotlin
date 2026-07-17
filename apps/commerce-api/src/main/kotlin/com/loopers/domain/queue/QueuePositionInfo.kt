package com.loopers.domain.queue

/**
 * 대기열 순번 조회 결과.
 *
 * @property position 현재 순번 (1-based). 0이면 입장 토큰 발급 완료. -1이면 대기열에 없음.
 * @property totalWaiting 전체 대기 인원
 * @property estimatedWaitSeconds 예상 대기 시간 (초)
 * @property token 입장 토큰 (발급된 경우에만 존재)
 */
data class QueuePositionInfo(
    val position: Long,
    val totalWaiting: Long,
    val estimatedWaitSeconds: Long,
    val token: String?,
)
