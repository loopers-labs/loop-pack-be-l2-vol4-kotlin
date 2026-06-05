package com.loopers.domain.order.infrastructure.persistence

import com.loopers.domain.order.model.OrderModel
import com.loopers.domain.order.port.OrderRepository
import org.springframework.stereotype.Component

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderItemJpaRepository: OrderItemJpaRepository,
) : OrderRepository {
    override fun save(order: OrderModel): OrderModel {
        val orderEntity = orderJpaRepository.saveAndFlush(OrderJpaEntity.fromDomain(order))
        val items = order.items.map { it.withOrderId(orderEntity.id) }
        orderItemJpaRepository.saveAllAndFlush(items.map { OrderItemJpaEntity.fromDomain(it) })
        return orderEntity.toDomain(items)
    }

    override fun findById(orderId: Long): OrderModel? =
        orderJpaRepository.findById(orderId)
            .map { order ->
                val items = orderItemJpaRepository.findByOrderItemIdOrderId(order.id)
                    .map { it.toDomain() }
                order.toDomain(items)
            }
            .orElse(null)

    override fun findByIdempotencyKey(idempotencyKey: String): OrderModel? =
        orderJpaRepository.findByIdempotencyKey(idempotencyKey)
            ?.let { order ->
                val items = orderItemJpaRepository.findByOrderItemIdOrderId(order.id)
                    .map { it.toDomain() }
                order.toDomain(items)
            }
}
