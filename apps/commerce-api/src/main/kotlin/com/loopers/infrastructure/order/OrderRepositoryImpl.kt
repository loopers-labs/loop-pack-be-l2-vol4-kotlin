package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun save(order: Order): Order {
        val entity = order.id
            ?.let { orderJpaRepository.findWithItemsByIdAndDeletedAtIsNull(it) }
            ?.also { it.updateFrom(order) }
            ?: OrderJpaEntity.from(order)

        return orderJpaRepository.save(entity).toDomain()
    }

    override fun find(id: Long): Order? {
        return orderJpaRepository.findWithItemsByIdAndDeletedAtIsNull(id)
            ?.toDomain()
    }

    override fun findPendingPaymentOlderThan(threshold: ZonedDateTime): List<Order> {
        return orderJpaRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            status = OrderStatus.PENDING_PAYMENT,
            createdAt = threshold,
        ).map { it.toDomain() }
    }

    override fun markPaidIfPending(id: Long): Boolean {
        return updateStatusIfPending(id = id, targetStatus = OrderStatus.PAID)
    }

    override fun markPaymentFailedIfPending(id: Long): Boolean {
        return updateStatusIfPending(id = id, targetStatus = OrderStatus.PAYMENT_FAILED)
    }

    override fun cancelIfPending(id: Long): Boolean {
        return updateStatusIfPending(id = id, targetStatus = OrderStatus.CANCELED)
    }

    private fun updateStatusIfPending(id: Long, targetStatus: OrderStatus): Boolean {
        return orderJpaRepository.updateStatusIfCurrent(
            id = id,
            currentStatus = OrderStatus.PENDING_PAYMENT,
            targetStatus = targetStatus,
        ) == 1
    }
}
