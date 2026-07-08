package com.loopers.domain.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * Transactional Outbox.
 * 도메인 변경과 "같은 트랜잭션"으로 기록되어, 외부(Kafka) 발행 대상 이벤트를 안전하게 적재한다.
 * 실제 발행은 별도 릴레이가 담당하며, At Least Once 를 보장한다.
 */
@Entity
@Table(
    name = "outbox_event",
    // 릴레이가 status 필터 + id 정렬(findByStatusOrderByIdAsc)로 조회하므로 (status, id) 복합 인덱스로 정렬까지 커버한다.
    indexes = [Index(name = "idx_outbox_event_status_id", columnList = "status, id")],
)
class OutboxEventModel(
    eventId: String,
    aggregateId: Long,
    eventType: String,
    topic: String,
    payload: String,
) : BaseEntity() {
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    var eventId: String = eventId
        protected set

    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long = aggregateId
        protected set

    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String = eventType
        protected set

    @Column(name = "topic", nullable = false, length = 100)
    var topic: String = topic
        protected set

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    var payload: String = payload
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING
        protected set

    fun markPublished() {
        status = OutboxStatus.PUBLISHED
    }
}

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
}
