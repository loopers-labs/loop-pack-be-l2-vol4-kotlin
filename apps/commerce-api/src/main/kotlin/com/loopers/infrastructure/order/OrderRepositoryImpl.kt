package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import org.springframework.stereotype.Component

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
}
