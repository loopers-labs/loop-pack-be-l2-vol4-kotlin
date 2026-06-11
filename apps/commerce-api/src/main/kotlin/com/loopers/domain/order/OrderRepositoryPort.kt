package com.loopers.domain.order

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import java.time.ZonedDateTime

interface OrderRepositoryPort {
    fun findById(id: Long): Order?
    fun save(order: Order): Order
    fun findAllByUserId(
        userId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
        pageRequest: PageRequest,
    ): PageResult<Order>
    fun findAll(pageRequest: PageRequest): PageResult<Order>
    fun existsByProductIdInAndStatusIn(
        productIds: List<Long>,
        statuses: Set<OrderStatus>,
    ): Boolean
}
