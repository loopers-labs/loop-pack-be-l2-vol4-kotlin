package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxModel
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Outbox 이벤트 JPA Repository.
 */
interface OutboxJpaRepository : JpaRepository<OutboxModel, Long> {
    /** 지정한 상태의 이벤트를 생성일 오름차순으로 조회한다. */
    fun findByStatusOrderByCreatedAtAsc(status: OutboxStatus, pageable: Pageable): List<OutboxModel>
}
