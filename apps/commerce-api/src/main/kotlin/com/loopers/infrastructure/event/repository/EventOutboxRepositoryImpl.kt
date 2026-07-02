package com.loopers.infrastructure.event.repository

import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.model.EventOutboxStatus
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.infrastructure.event.mapper.EventOutboxMapper
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class EventOutboxRepositoryImpl(
    private val eventOutboxJpaRepository: EventOutboxJpaRepository,
) : EventOutboxRepository {
    override fun save(eventOutbox: EventOutbox): EventOutbox {
        val entity = if (eventOutbox.id == 0L) {
            EventOutboxMapper.toEntity(eventOutbox)
        } else {
            eventOutboxJpaRepository.findByIdOrNull(eventOutbox.id)
                ?.also { it.update(eventOutbox) }
                ?: throw CoreException(ErrorType.NOT_FOUND, "Event outbox not found.")
        }

        return eventOutboxJpaRepository.save(entity)
            .let(EventOutboxMapper::toDomain)
    }

    override fun findByEventId(eventId: String): EventOutbox? {
        return eventOutboxJpaRepository.findByEventId(eventId)
            ?.let(EventOutboxMapper::toDomain)
    }

    override fun findPending(limit: Int): List<EventOutbox> {
        if (limit <= 0) {
            return emptyList()
        }

        return eventOutboxJpaRepository
            .findAllByStatusOrderByIdAsc(EventOutboxStatus.PENDING, PageRequest.of(0, limit))
            .map(EventOutboxMapper::toDomain)
    }
}
