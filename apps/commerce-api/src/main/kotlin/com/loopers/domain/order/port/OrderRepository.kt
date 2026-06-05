package com.loopers.domain.order.port

import com.loopers.domain.order.model.OrderModel

interface OrderRepository {
    fun save(order: OrderModel): OrderModel
    fun findById(orderId: Long): OrderModel?
    fun findByIdempotencyKey(idempotencyKey: String): OrderModel?
}
