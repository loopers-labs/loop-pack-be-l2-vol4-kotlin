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

    override fun completePaymentPending(orderId: Long, paymentTransactionId: String): Int =
        orderJpaRepository.completePaymentPending(
            orderId = orderId,
            paymentTransactionId = paymentTransactionId,
            currentStatus = OrderStatus.PAYMENT_PENDING,
            nextStatus = OrderStatus.COMPLETED,
        )

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

    override fun startShippingCompleted(orderId: Long): Int =
        orderJpaRepository.startShippingCompleted(
            orderId = orderId,
            currentStatus = OrderStatus.COMPLETED,
            nextStatus = OrderStatus.SHIPPING_STARTED,
        )

    override fun findExpiredPaymentPending(now: LocalDateTime): List<Order> =
        orderJpaRepository.findAllByStatusAndReservationExpiresAtBefore(OrderStatus.PAYMENT_PENDING, now)
}
