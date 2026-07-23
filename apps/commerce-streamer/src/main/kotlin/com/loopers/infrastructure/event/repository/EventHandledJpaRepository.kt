package com.loopers.infrastructure.event.repository

import com.loopers.infrastructure.event.entity.EventHandledEntity
import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledJpaRepository : JpaRepository<EventHandledEntity, Long> {
    fun findByConsumerGroupAndEventId(
        consumerGroup: String,
        eventId: String,
    ): EventHandledEntity?

    fun existsByConsumerGroupAndEventId(
        consumerGroup: String,
        eventId: String,
    ): Boolean
}
