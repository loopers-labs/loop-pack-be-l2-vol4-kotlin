package com.loopers.domain.order

import org.springframework.data.domain.Page

interface OrderRepository {
    fun findAll(page: Int, size: Int): Page<Order>

    fun findById(orderId: Long): Order?

    fun findAllByMemberIdAndOrderedAtBetween(
        memberId: Long,
        startAt: java.time.ZonedDateTime,
        endAt: java.time.ZonedDateTime,
    ): List<Order>

    fun findByMemberIdAndId(memberId: Long, orderId: Long): Order?

    fun save(order: Order): Order
}
