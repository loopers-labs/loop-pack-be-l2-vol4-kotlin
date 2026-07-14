package com.loopers.domain.order.infrastructure.persistence

import com.loopers.domain.order.model.OrderModel
import com.loopers.domain.order.port.OrderRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

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

    override fun update(order: OrderModel): OrderModel {
        val entity = orderJpaRepository.findById(order.id).orElseThrow()
        entity.issuedCouponId = order.issuedCouponId
        entity.status = order.status
        entity.totalPrice = order.totalPrice.value
        entity.discountPrice = order.discountPrice.value
        entity.paymentPrice = order.paymentPrice.value
        return orderJpaRepository.saveAndFlush(entity).toDomain(order.items)
    }

    override fun findByIdOrNull(orderId: Long): OrderModel? =
        orderJpaRepository.findById(orderId)
            .map { order ->
                val items = orderItemJpaRepository.findByOrderItemIdOrderId(order.id)
                    .map { it.toDomain() }
                order.toDomain(items)
            }
            .orElse(null)

    override fun findByOrderedUserIdAndIdempotencyKeyOrNull(
        orderedUserId: Long,
        idempotencyKey: String,
    ): OrderModel? =
        orderJpaRepository.findByOrderedUserIdAndIdempotencyKey(orderedUserId, idempotencyKey)
            ?.let { order ->
                val items = orderItemJpaRepository.findByOrderItemIdOrderId(order.id)
                    .map { it.toDomain() }
                order.toDomain(items)
            }

    override fun findByOrderedUserId(
        orderedUserId: Long,
        startAt: ZonedDateTime?,
        endAt: ZonedDateTime?,
    ): List<OrderModel> =
        orderJpaRepository.findByOrderedUserId(orderedUserId, startAt, endAt)
            .toDomainsWithItems()

    override fun findAll(page: Int, size: Int): List<OrderModel> =
        orderJpaRepository.findAllByCreatedAtDesc(PageRequest.of(page, size))
            .toDomainsWithItems()

    private fun OrderJpaEntity.toDomainWithItems(): OrderModel {
        val items = orderItemJpaRepository.findByOrderItemIdOrderId(id).map { it.toDomain() }
        return toDomain(items)
    }

    private fun List<OrderJpaEntity>.toDomainsWithItems(): List<OrderModel> {
        if (isEmpty()) {
            return emptyList()
        }
        val itemsByOrderId = orderItemJpaRepository.findByOrderItemIdOrderIdIn(map { it.id })
            .map { it.toDomain() }
            .groupBy { it.orderId }
        return map { order -> order.toDomain(itemsByOrderId[order.id].orEmpty()) }
    }
}
