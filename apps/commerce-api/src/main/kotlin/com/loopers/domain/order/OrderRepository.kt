package com.loopers.domain.order

interface OrderRepository {
    fun findAllByMemberIdAndOrderedAtBetween(
        memberId: Long,
        startAt: java.time.ZonedDateTime,
        endAt: java.time.ZonedDateTime,
    ): List<Order>

    fun findByMemberIdAndId(memberId: Long, orderId: Long): Order?

    fun save(order: Order): Order
}
