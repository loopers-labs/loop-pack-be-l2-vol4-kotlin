package com.loopers.application.event

import com.loopers.domain.event.ExternalEventPublisher
import com.loopers.domain.event.repository.EventOutboxRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class EventOutboxRelayService(
    private val eventOutboxRepository: EventOutboxRepository,
    private val externalEventPublisher: ExternalEventPublisher,
) {
    @Transactional
    fun relayPending(limit: Int): Int {
        val pendingEvents = eventOutboxRepository.findPending(limit)

        pendingEvents.forEach { eventOutbox ->
            runCatching {
                externalEventPublisher.publish(eventOutbox)
                eventOutboxRepository.save(eventOutbox.published())
            }.onFailure {
                eventOutboxRepository.save(eventOutbox.failed())
            }
        }

        return pendingEvents.size
    }
}
