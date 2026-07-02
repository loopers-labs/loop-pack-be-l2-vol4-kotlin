package com.loopers.order.domain

import java.time.LocalDateTime

interface OrderRepository {
    fun save(order: Order): Order

    fun findById(id: Long): Order?

    fun findByOrderKey(orderKey: String): Order?

    fun findByUserIdAndOrderedAtBetween(userId: Long, startAt: LocalDateTime, endAt: LocalDateTime): List<Order>
}
