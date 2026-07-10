package com.loopers.domain.waitingqueue.config.constant

object WaitingQueueConfigErrorMessages {
    const val TOKEN_TTL_MUST_BE_AT_LEAST_ONE_MILLISECOND = "token-ttl은 1ms 이상이어야 합니다."
    const val SCHEDULER_DELAY_MUST_BE_AT_LEAST_ONE_MILLISECOND = "scheduler-delay는 1ms 이상이어야 합니다."
    const val ADMISSION_BATCH_SIZE_MUST_BE_POSITIVE = "admission-batch-size는 0보다 커야 합니다."
    const val SCHEDULER_JITTER_MUST_NOT_BE_NEGATIVE = "scheduler-jitter-max는 음수일 수 없습니다."
    const val SCHEDULER_JITTER_MUST_BE_SHORTER_THAN_TTL = "scheduler-jitter-max는 token-ttl보다 작아야 합니다."
    const val POLLING_INTERVAL_MUST_BE_AT_LEAST_ONE_SECOND = "polling-interval은 1초 이상이어야 합니다."
    const val TOKEN_PREFIX_REQUIRED = "token-prefix는 비어 있을 수 없습니다."
    const val REDIS_KEY_PREFIX_REQUIRED = "redis-key-prefix는 비어 있을 수 없습니다."
    const val REDIS_KEY_REQUIRED = "Redis key 설정은 비어 있을 수 없습니다."
    const val REDIS_KEYS_MUST_BE_DISTINCT = "Redis key 설정은 서로 달라야 합니다."
    const val REDIS_KEYS_MUST_NOT_OVERLAP = "정적 Redis key와 동적 key prefix는 계층적으로 겹칠 수 없습니다."
}
