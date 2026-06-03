package com.loopers.infrastructure.order

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    fun findAllByUserIdAndOrderedAtBetween(
        userId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
        pageable: Pageable,
    ): Page<OrderEntity>

    fun existsByIdInAndStatusIn(ids: Collection<Long>, statuses: Collection<OrderStatusType>): Boolean
}
