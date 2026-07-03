package com.loopers.domain.eventhandled

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

/**
 * 멱등 처리 저장소.
 * event_id 를 PK 로 두어, 이미 처리한 이벤트의 재수신(At Least Once) 을 차단한다.
 * 집계 반영과 "같은 트랜잭션"으로 기록되어 정합성을 보장한다.
 */
@Entity
@Table(name = "event_handled")
class EventHandledModel(
    @Id
    @Column(name = "event_id", length = 36)
    val eventId: String,
    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,
    @Column(name = "handled_at", nullable = false)
    val handledAt: ZonedDateTime,
)
