package com.loopers.application.outbox

import com.loopers.domain.outbox.Outbox
import com.loopers.domain.outbox.OutboxRepositoryPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class OutboxWriter(
    private val outboxRepositoryPort: OutboxRepositoryPort,
) {
    @Transactional
    fun save(outbox: Outbox): Outbox = outboxRepositoryPort.save(outbox)

    @Transactional
    fun markPublished(eventId: String) = outboxRepositoryPort.markPublished(eventId)

    @Transactional(readOnly = true)
    fun findAllCreatedBefore(threshold: ZonedDateTime): List<Outbox> =
        outboxRepositoryPort.findAllCreatedBefore(threshold)
}
