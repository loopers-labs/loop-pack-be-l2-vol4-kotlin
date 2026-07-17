package com.loopers.infrastructure.idempotency

import com.loopers.domain.idempotency.EventHandledModel
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 이벤트 멱등 처리 JPA Repository.
 */
interface EventHandledJpaRepository : JpaRepository<EventHandledModel, Long> {
    /** eventId 존재 여부를 확인한다. */
    fun existsByEventId(eventId: String): Boolean
}
