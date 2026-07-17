package com.loopers.domain.idempotency

/**
 * 이벤트 멱등 처리 저장소 인터페이스.
 */
interface EventHandledRepository {
    /** 해당 eventId가 이미 처리되었는지 확인한다. */
    fun existsByEventId(eventId: String): Boolean

    /** 처리 완료된 이벤트를 기록한다. */
    fun save(model: EventHandledModel): EventHandledModel
}
