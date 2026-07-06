package com.loopers.outbox.domain

// 시스템 경계를 넘어 전파되어야 하는 이벤트만 구현한다 — 구현하면 outbox 에 본 트랜잭션과 원자적으로 적재된다.
// 같은 앱 내부 부가 처리(like_count 비정규화 등)용 이벤트는 구현하지 않는다.
interface OutboxPublishable {
    // 컨슈머 멱등(dedup) 키 — 이벤트 객체 생성 시 UUID. outbox 행 id 가 아니라 이 값이 event_handled 의 PK 가 된다.
    val eventId: String

    val aggregateType: String

    val aggregateId: Long

    val eventType: String
}
