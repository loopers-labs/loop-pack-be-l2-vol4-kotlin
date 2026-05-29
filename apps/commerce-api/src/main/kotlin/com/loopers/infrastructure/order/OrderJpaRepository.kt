package com.loopers.infrastructure.order

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    @EntityGraph(attributePaths = ["items"])
    fun findAllByMemberIdAndOrderedAtBetweenOrderByOrderedAtDescIdDesc(
        memberId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
    ): List<OrderEntity>

    @EntityGraph(attributePaths = ["items"])
    fun findByMemberIdAndId(memberId: Long, orderId: Long): OrderEntity?
}
