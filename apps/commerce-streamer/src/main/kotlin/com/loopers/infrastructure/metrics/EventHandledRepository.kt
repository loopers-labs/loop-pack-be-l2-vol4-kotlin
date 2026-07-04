package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.EventHandledModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.ZonedDateTime

interface EventHandledRepository : JpaRepository<EventHandledModel, String> {
    @Modifying(clearAutomatically = true)
    @Query(
        value = "INSERT IGNORE INTO event_handled (event_id, handled_at) VALUES (:eventId, :now)",
        nativeQuery = true,
    )
    fun insertIfAbsent(eventId: String, now: ZonedDateTime): Int
}
