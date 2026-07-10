package com.loopers.domain.waitingqueue.infrastructure.redis.constant

object WaitingQueueRedisConstants {
    const val STATUS_FIELD = "status"
    const val TOKEN_FIELD = "token"
    const val USER_ID_FIELD = "userId"
    const val SEQUENCE_FIELD = "sequence"
    const val AVAILABLE_AT_FIELD = "availableAt"
    const val EXPIRES_AT_FIELD = "expiresAt"
    const val IDEMPOTENCY_KEY_FIELD = "idempotencyKey"

    const val ACTIVE_STATUS = "ACTIVE"
    const val PROCESSING_STATUS = "PROCESSING"
    const val CONSUMED_STATUS = "CONSUMED"
    const val WAITING_QUEUE_STATUS = "WAITING"
    const val ADMITTED_QUEUE_STATUS = "ADMITTED"

    const val TOKEN_VALID = 1L
    const val TOKEN_CONSUMED_BY_SAME_KEY = 2L
    const val TOKEN_RESERVED_OR_CONSUMED_BY_OTHER_KEY = 3L
    const val TOKEN_INVALID = 4L
    const val TOKEN_NOT_YET_AVAILABLE = 5L
    const val TOKEN_PROCESSING_BY_SAME_KEY = 6L
    const val TOKEN_TRANSITION_SUCCEEDED = 1L

    const val ENQUEUE_STATE_RETRY_LIMIT = 2

    const val OPERATION_FAILED_MESSAGE = "Redis 대기열 작업에 실패했습니다."
    const val ENQUEUE_STATE_RACE_MESSAGE = "입장 상태 만료 경계에서 대기열 진입 상태를 확정하지 못했습니다."
}
