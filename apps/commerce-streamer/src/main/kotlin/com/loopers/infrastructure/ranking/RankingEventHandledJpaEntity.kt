package com.loopers.infrastructure.ranking

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "ranking_event_handled")
class RankingEventHandledJpaEntity(
    @Id
    @Column(name = "event_id", length = 36)
    val eventId: String,
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: String,
) {
    @Column(name = "handled_at", nullable = false, updatable = false)
    lateinit var handledAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersist() {
        handledAt = ZonedDateTime.now()
    }
}
