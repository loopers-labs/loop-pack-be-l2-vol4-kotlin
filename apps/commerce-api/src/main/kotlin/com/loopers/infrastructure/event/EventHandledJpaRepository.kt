package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandledModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.ZonedDateTime

interface EventHandledJpaRepository : JpaRepository<EventHandledModel, String> {
    @Modifying(clearAutomatically = true)
    @Query(
        value = "INSERT IGNORE INTO event_handled (event_id, handled_at) VALUES (:eventId, :now)",
        nativeQuery = true,
    )
    fun insertIfAbsent(eventId: String, now: ZonedDateTime): Int
}
