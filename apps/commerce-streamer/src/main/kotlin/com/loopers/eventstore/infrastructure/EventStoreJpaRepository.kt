package com.loopers.eventstore.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface EventStoreJpaRepository : JpaRepository<EventStoreRecord, String> {
    @Modifying
    @Query(
        value = """
            insert into event_store (event_id, topic, payload, recorded_at)
            values (:eventId, :topic, :payload, now())
            on duplicate key update event_id = event_id
        """,
        nativeQuery = true,
    )
    fun appendIgnoringDuplicate(eventId: String, topic: String, payload: String): Int
}
