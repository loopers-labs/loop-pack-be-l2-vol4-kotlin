package com.loopers.infrastructure.eventhandled

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventHandledJpaRepository : JpaRepository<EventHandledJpaEntity, String> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            insert ignore into event_handled (event_id, event_type, handled_at)
            values (:eventId, :eventType, current_timestamp(6))
        """,
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("eventId") eventId: String,
        @Param("eventType") eventType: String,
    ): Int
}
