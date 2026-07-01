package com.loopers.domain.order.port

import com.loopers.domain.order.model.OrderModel
import java.time.ZonedDateTime

interface OrderRepository {
    fun save(order: OrderModel): OrderModel
    fun update(order: OrderModel): OrderModel
    fun findByIdOrNull(orderId: Long): OrderModel?
    fun findByIdempotencyKeyOrNull(idempotencyKey: String): OrderModel?
    fun findByOrderedUserId(
        orderedUserId: Long,
        startAt: ZonedDateTime?,
        endAt: ZonedDateTime?,
    ): List<OrderModel>
    fun findAll(page: Int, size: Int): List<OrderModel>
}
