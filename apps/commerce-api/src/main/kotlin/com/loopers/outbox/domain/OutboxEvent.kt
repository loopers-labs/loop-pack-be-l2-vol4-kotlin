package com.loopers.outbox.domain

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

// Transactional Outbox: 비즈니스 write 와 같은 트랜잭션으로 INSERT 되어 at-least-once 발행을 보장한다.
// SENT 전이는 폴링 릴레이가 담당 — 발생 시각 = BaseEntity.createdAt.
@Entity
@Table(
    name = "outbox_event",
    indexes = [Index(name = "idx_outbox_event_status_id", columnList = "status, id")],
)
class OutboxEvent(
    aggregateType: String,
    aggregateId: Long,
    eventType: String,
    payload: String,
) : BaseEntity() {
    @Column(name = "aggregate_type", nullable = false, length = 30, updatable = false)
    val aggregateType: String = aggregateType

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    val aggregateId: Long = aggregateId

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    val eventType: String = eventType

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    val payload: String = payload

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: OutboxStatus = OutboxStatus.INIT
        private set
}

enum class OutboxStatus { INIT, SENT }
