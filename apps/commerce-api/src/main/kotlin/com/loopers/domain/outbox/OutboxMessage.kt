package com.loopers.domain.outbox

import java.time.ZonedDateTime

/**
 * Kafka 로 발행되는 와이어 메시지(봉투).
 * 내부 도메인 이벤트를 감싸, 소비자가 필요로 하는 메타데이터를 함께 실어 보낸다.
 *
 * @property eventId   멱등 처리의 근거 (소비자 event_handled PK)
 * @property occurredAt 최신 이벤트만 반영(version/updated_at 비교)의 근거
 * @property payload   이벤트별 실제 데이터 (도메인 이벤트 스냅샷)
 */
data class OutboxMessage(
    val eventId: String,
    val eventType: String,
    val aggregateId: Long,
    val occurredAt: ZonedDateTime,
    val payload: Any,
)
