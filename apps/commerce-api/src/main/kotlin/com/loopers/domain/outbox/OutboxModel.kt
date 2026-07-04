package com.loopers.domain.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "outbox")
class OutboxModel private constructor(
    aggregateId: String,
    eventType: String,
    topic: String,
    payload: String,
) : BaseEntity() {
    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: String = aggregateId
        protected set

    @Column(name = "event_type", nullable = false)
    var eventType: String = eventType
        protected set

    @Column(name = "topic", nullable = false)
    var topic: String = topic
        protected set

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    var payload: String = payload
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxStatus = OutboxStatus.NEW
        protected set

    fun markSent() {
        this.status = OutboxStatus.SENT
    }

    companion object {
        fun of(aggregateId: String, eventType: String, topic: String, payload: String): OutboxModel =
            OutboxModel(aggregateId, eventType, topic, payload)
    }
}
