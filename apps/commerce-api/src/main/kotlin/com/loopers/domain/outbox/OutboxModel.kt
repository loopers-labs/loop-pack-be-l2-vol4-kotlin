package com.loopers.domain.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * Transactional Outbox 패턴의 이벤트 저장 엔티티.
 * 도메인 트랜잭션과 동일한 트랜잭션 내에 이벤트를 기록하고,
 * 별도 릴레이(OutboxRelay)가 PENDING 상태인 레코드를 Kafka로 발행한다.
 */
@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
    ],
)
class OutboxModel(
    topic: String,
    partitionKey: String,
    eventId: String,
    payload: String,
) : BaseEntity() {

    /** 발행 대상 Kafka 토픽 */
    @Column(name = "topic", nullable = false)
    var topic: String = topic
        protected set

    /** Kafka 파티션 키 (순서 보장 단위) */
    @Column(name = "partition_key", nullable = false)
    var partitionKey: String = partitionKey
        protected set

    /** 이벤트 고유 ID (Consumer 멱등 처리 키) */
    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: String = eventId
        protected set

    /** JSON 직렬화된 이벤트 페이로드 */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    var payload: String = payload
        protected set

    /** 현재 발행 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxStatus = OutboxStatus.PENDING
        protected set

    /** Kafka 발행 성공 시 호출 */
    fun markPublished() {
        status = OutboxStatus.PUBLISHED
    }

    /** Kafka 발행 실패 시 호출 */
    fun markFailed() {
        status = OutboxStatus.FAILED
    }
}

/**
 * Outbox 레코드 상태.
 */
enum class OutboxStatus {
    /** 발행 대기 */
    PENDING,

    /** 발행 완료 */
    PUBLISHED,

    /** 발행 실패 (재시도 또는 수동 개입 필요) */
    FAILED,
}
