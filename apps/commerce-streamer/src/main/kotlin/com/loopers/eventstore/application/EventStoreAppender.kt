package com.loopers.eventstore.application

import com.loopers.eventstore.infrastructure.EventStoreJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventStoreAppender(
    private val eventStoreJpaRepository: EventStoreJpaRepository,
) {
    @Transactional
    fun append(eventId: String, topic: String, payload: ByteArray) {
        eventStoreJpaRepository.appendIgnoringDuplicate(eventId, topic, String(payload, Charsets.UTF_8))
    }
}
