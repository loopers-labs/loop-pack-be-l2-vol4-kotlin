package com.loopers.domain.waitingqueue.constant

object WaitingQueueErrorMessages {
    const val ENTRY_NOT_FOUND = "대기열에서 사용자를 찾을 수 없습니다."
    const val AVAILABLE_AT_AFTER_EXPIRATION = "입장 가능 시각은 토큰 만료 시각보다 늦을 수 없습니다."
    const val TOKEN_CONSUME_TRANSITION_FAILED = "입장 토큰을 소비 상태로 전환하지 못했습니다."
    const val TOKEN_RELEASE_TRANSITION_FAILED = "입장 토큰 예약을 해제하지 못했습니다."
    const val TOKEN_NOT_YET_AVAILABLE = "입장 토큰의 사용 가능 시각이 아직 되지 않았습니다."
    const val TOKEN_REQUIRED = "대기열 토큰은 비어 있을 수 없습니다."
    const val SEQUENCE_MUST_BE_POSITIVE = "대기열 sequence는 양수여야 합니다."
}
