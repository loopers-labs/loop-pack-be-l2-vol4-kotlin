package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderItemJpaRepository: OrderItemJpaRepository,
) : OrderRepository {
    override fun save(order: Order): Order = orderJpaRepository.save(order)

    override fun saveItems(items: List<OrderItem>): List<OrderItem> = orderItemJpaRepository.saveAll(items)

    override fun findById(orderId: Long): Order? = orderJpaRepository.findByIdOrNull(orderId)

    override fun findItemsByOrderId(orderId: Long): List<OrderItem> = orderItemJpaRepository.findAllByOrderId(orderId)

    override fun findByProductId(productId: Long): List<Order> {
        val orderIds = orderItemJpaRepository.findAllByProductId(productId)
            .map { it.orderId }
            .distinct()
        if (orderIds.isEmpty()) return emptyList()
        return orderJpaRepository.findAllById(orderIds).sortedBy { it.id }
    }

    override fun completeFromPaymentPending(orderId: Long): Int =
        orderJpaRepository.updateStatus(orderId, OrderStatus.PAYMENT_PENDING, OrderStatus.COMPLETED)

    override fun completeFromFailed(orderId: Long): Int =
        orderJpaRepository.updateStatus(orderId, OrderStatus.FAILED, OrderStatus.COMPLETED)

    override fun markCompletionFailed(orderId: Long): Int =
        orderJpaRepository.updateStatus(orderId, OrderStatus.PAYMENT_PENDING, OrderStatus.FAILED)

    override fun markCompletedAsFailed(orderId: Long): Int =
        orderJpaRepository.updateStatus(orderId, OrderStatus.COMPLETED, OrderStatus.FAILED)

    override fun expirePaymentPending(orderId: Long): Int =
        orderJpaRepository.updateStatus(orderId, OrderStatus.PAYMENT_PENDING, OrderStatus.EXPIRED)

    override fun cancelPaymentPending(orderId: Long, reason: OrderCancelReason): Int =
        orderJpaRepository.cancelByCurrentStatus(
            orderId = orderId,
            cancelReason = reason,
            currentStatus = OrderStatus.PAYMENT_PENDING,
            nextStatus = OrderStatus.CANCELED,
        )

    override fun cancelCompleted(orderId: Long, reason: OrderCancelReason): Int =
        orderJpaRepository.cancelByCurrentStatus(
            orderId = orderId,
            cancelReason = reason,
            currentStatus = OrderStatus.COMPLETED,
            nextStatus = OrderStatus.CANCELED,
        )

    override fun cancelFailedByOperator(orderId: Long, reason: OrderCancelReason): Int =
        orderJpaRepository.cancelByCurrentStatus(
            orderId = orderId,
            cancelReason = reason,
            currentStatus = OrderStatus.FAILED,
            nextStatus = OrderStatus.CANCELED,
        )

    override fun startShippingCompleted(orderId: Long): Int =
        orderJpaRepository.updateStatus(orderId, OrderStatus.COMPLETED, OrderStatus.SHIPPING_STARTED)

    override fun findExpiredPaymentPending(now: LocalDateTime): List<Order> =
        orderJpaRepository.findAllByStatusAndReservationExpiresAtBefore(OrderStatus.PAYMENT_PENDING, now)
}
