package com.loopers.domain.idempotency

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

/**
 * 이벤트 멱등 처리 테이블.
 * Consumer가 이벤트를 처리할 때 eventId를 기록하여 중복 처리를 방지한다.
 * event_id에 unique 제약이 걸려 있으므로 같은 이벤트가 두 번 처리되지 않는다.
 */
@Entity
@Table(name = "event_handled")
class EventHandledModel(
    eventId: String,
    topic: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    /** Outbox에서 부여한 이벤트 고유 ID */
    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: String = eventId
        protected set

    /** 소비한 토픽 이름 */
    @Column(name = "topic", nullable = false)
    var topic: String = topic
        protected set

    /** 처리 시각 */
    @Column(name = "handled_at", nullable = false)
    var handledAt: ZonedDateTime = ZonedDateTime.now()
        protected set
}
