package com.loopers.domain.order

interface OrderRepository {
    fun save(order: Order): Order

    fun find(id: Long): Order?

    fun markPaidIfPending(id: Long): Boolean

    fun markPaymentFailedIfPending(id: Long): Boolean

    fun cancelIfPending(id: Long): Boolean
}
