package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.EventHandled
import com.loopers.domain.metrics.EventHandledRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class EventHandledRepositoryImpl(
    private val jpa: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun markHandled(eventId: String): Boolean {
        if (jpa.existsById(eventId)) return false
        return try {
            jpa.save(EventHandled(eventId))
            true
        } catch (e: DataIntegrityViolationException) {
            false // 동시 중복 삽입 — 이미 처리됨
        }
    }
}
