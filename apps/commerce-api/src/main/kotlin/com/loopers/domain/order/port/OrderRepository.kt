package com.loopers.domain.order.port

import com.loopers.domain.order.model.OrderModel
import java.time.ZonedDateTime

interface OrderRepository {
    fun save(order: OrderModel): OrderModel
    fun findById(orderId: Long): OrderModel?
    fun findByIdempotencyKey(idempotencyKey: String): OrderModel?
    fun findByOrderedUserId(
        orderedUserId: Long,
        startAt: ZonedDateTime?,
        endAt: ZonedDateTime?,
    ): List<OrderModel>
    fun findAll(page: Int, size: Int): List<OrderModel>
}
