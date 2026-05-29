package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findByUserIdAndOrderedAtBetween(userId: Long, startAt: LocalDateTime, endAt: LocalDateTime): List<Order>
}
