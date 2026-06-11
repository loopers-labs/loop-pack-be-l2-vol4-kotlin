package com.loopers.domain.order

import java.time.LocalDateTime

interface OrderRepository {
    fun save(order: Order): Order

    fun saveItems(items: List<OrderItem>): List<OrderItem>

    fun findById(orderId: Long): Order?

    fun findItemsByOrderId(orderId: Long): List<OrderItem>

    fun findByProductId(productId: Long): List<Order>

    fun completeFromPaymentPending(orderId: Long): Int

    fun completeFromFailed(orderId: Long): Int

    fun markCompletionFailed(orderId: Long): Int

    fun markCompletedAsFailed(orderId: Long): Int

    fun expirePaymentPending(orderId: Long): Int

    fun cancelPaymentPending(orderId: Long, reason: OrderCancelReason): Int

    fun cancelCompleted(orderId: Long, reason: OrderCancelReason): Int

    fun cancelFailedByOperator(orderId: Long, reason: OrderCancelReason): Int

    fun startShippingCompleted(orderId: Long): Int

    fun findExpiredPaymentPending(now: LocalDateTime): List<Order>
}
