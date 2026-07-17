package com.loopers.support.outbox

enum class OutboxEventStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    DEAD,
}
