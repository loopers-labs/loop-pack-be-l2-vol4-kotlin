package com.loopers.domain.outbox

/**
 * Outbox 이벤트 저장소 인터페이스.
 */
interface OutboxRepository {
    /** Outbox 레코드를 저장한다. */
    fun save(outbox: OutboxModel): OutboxModel

    /** PENDING 상태인 이벤트를 생성 순으로 최대 [limit]개 조회한다. */
    fun findPendingEvents(limit: Int): List<OutboxModel>
}
