package com.loopers.infrastructure.outbox

import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface OutboxJpaRepository : JpaRepository<OutboxEntity, Long> {
    fun findByEventId(eventId: String): OutboxEntity?
    fun findAllByStatusAndCreatedAtBefore(status: PersistedOutboxStatus, threshold: ZonedDateTime): List<OutboxEntity>
}
