package com.loopers.domain.order.repository

import com.loopers.domain.order.model.Order
import org.springframework.data.domain.Page
import java.time.ZonedDateTime

interface OrderRepository {
    fun findAll(page: Int, size: Int): Page<Order>

    fun findById(orderId: Long): Order?

    fun findByIdForUpdate(orderId: Long): Order?

    fun findByOrderNumber(orderNumber: String): Order?

    fun findByOrderNumberForUpdate(orderNumber: String): Order?

    fun findAllByMemberIdAndOrderedAtBetween(
        memberId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
    ): List<Order>

    fun findByMemberIdAndId(memberId: Long, orderId: Long): Order?

    fun save(order: Order): Order

    fun updateStatus(order: Order): Order
}
