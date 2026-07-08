package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxStatus

enum class PersistedOutboxStatus {
    CREATED,
    PUBLISHED,
    ;

    fun toDomain(): OutboxStatus = when (this) {
        CREATED -> OutboxStatus.CREATED
        PUBLISHED -> OutboxStatus.PUBLISHED
    }

    companion object {
        fun from(domain: OutboxStatus): PersistedOutboxStatus = when (domain) {
            OutboxStatus.CREATED -> CREATED
            OutboxStatus.PUBLISHED -> PUBLISHED
        }
    }
}
