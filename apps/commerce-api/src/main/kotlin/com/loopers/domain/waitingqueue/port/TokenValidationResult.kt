package com.loopers.domain.waitingqueue.port

class TokenValidationResult private constructor(
    val status: Status,
    val consumedByIdempotencyKey: String? = null,
) {
    val isAllowed: Boolean
        get() = status in ALLOWED_STATUSES

    enum class Status {
        VALID,
        PROCESSING_BY_SAME_IDEMPOTENCY_KEY,
        CONSUMED_BY_SAME_IDEMPOTENCY_KEY,
        CONSUMED_BY_DIFFERENT_IDEMPOTENCY_KEY,
        INVALID,
        NOT_YET_AVAILABLE,
    }

    companion object {
        private val ALLOWED_STATUSES = setOf(
            Status.VALID,
            Status.PROCESSING_BY_SAME_IDEMPOTENCY_KEY,
            Status.CONSUMED_BY_SAME_IDEMPOTENCY_KEY,
        )

        fun valid(): TokenValidationResult = TokenValidationResult(Status.VALID)

        fun processingBySameIdempotencyKey(idempotencyKey: String): TokenValidationResult =
            TokenValidationResult(Status.PROCESSING_BY_SAME_IDEMPOTENCY_KEY, idempotencyKey)

        fun consumedBySameIdempotencyKey(idempotencyKey: String): TokenValidationResult =
            TokenValidationResult(Status.CONSUMED_BY_SAME_IDEMPOTENCY_KEY, idempotencyKey)

        fun consumedByDifferentIdempotencyKey(consumedByIdempotencyKey: String?): TokenValidationResult =
            TokenValidationResult(Status.CONSUMED_BY_DIFFERENT_IDEMPOTENCY_KEY, consumedByIdempotencyKey)

        fun invalid(): TokenValidationResult = TokenValidationResult(Status.INVALID)

        fun notYetAvailable(): TokenValidationResult = TokenValidationResult(Status.NOT_YET_AVAILABLE)
    }
}
