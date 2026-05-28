package com.loopers.infrastructure.order

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepositoryPort
import com.loopers.domain.order.OrderStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import org.springframework.data.domain.PageRequest as SpringPageRequest

@Component
class OrderRepositoryAdapter(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderItemJpaRepository: OrderItemJpaRepository,
) : OrderRepositoryPort {

    override fun findById(id: Long): Order? {
        val entity = orderJpaRepository.findById(id).orElse(null) ?: return null
        val items = orderItemJpaRepository.findAllByOrderId(entity.id)
        return entity.toDomain(items)
    }

    override fun save(order: Order): Order {
        val orderEntity = if (order.id == 0L) {
            val saved = orderJpaRepository.save(OrderEntity.from(order))
            val itemEntities = order.items.list.map { OrderItemEntity.from(saved.id, it) }
            orderItemJpaRepository.saveAll(itemEntities)
            saved
        } else {
            val existing = orderJpaRepository.findById(order.id)
                .orElseThrow { CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.") }
            existing.updateStatus(OrderStatusType.from(order.status))
            orderJpaRepository.save(existing)
        }
        val items = orderItemJpaRepository.findAllByOrderId(orderEntity.id)
        return orderEntity.toDomain(items)
    }

    override fun findAllByUserId(
        userId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
        pageRequest: PageRequest,
    ): PageResult<Order> {
        val springPageable = SpringPageRequest.of(
            pageRequest.page,
            pageRequest.size,
            Sort.by(Sort.Direction.DESC, "orderedAt", "id"),
        )
        val page = orderJpaRepository.findAllByUserIdAndOrderedAtBetween(userId, startAt, endAt, springPageable)
        return toPageResult(page.content, page.totalElements, pageRequest)
    }

    override fun findAll(pageRequest: PageRequest): PageResult<Order> {
        val springPageable = SpringPageRequest.of(
            pageRequest.page,
            pageRequest.size,
            Sort.by(Sort.Direction.DESC, "id"),
        )
        val page = orderJpaRepository.findAll(springPageable)
        return toPageResult(page.content, page.totalElements, pageRequest)
    }

    override fun existsByProductIdInAndStatusIn(productIds: List<Long>, statuses: Set<OrderStatus>): Boolean {
        if (productIds.isEmpty() || statuses.isEmpty()) return false
        val orderIds = orderItemJpaRepository.findOrderIdsByProductIdIn(productIds)
        if (orderIds.isEmpty()) return false
        val statusTypes = statuses.map { OrderStatusType.from(it) }
        return orderJpaRepository.existsByIdInAndStatusIn(orderIds, statusTypes)
    }

    private fun toPageResult(
        entities: List<OrderEntity>,
        totalElements: Long,
        pageRequest: PageRequest,
    ): PageResult<Order> {
        if (entities.isEmpty()) {
            return PageResult.of(items = emptyList(), pageRequest = pageRequest, totalElements = totalElements)
        }
        val orderIds = entities.map { it.id }
        val itemsByOrderId = orderItemJpaRepository.findAllByOrderIdIn(orderIds).groupBy { it.orderId }
        val orders = entities.map { entity ->
            entity.toDomain(itemsByOrderId[entity.id] ?: emptyList())
        }
        return PageResult.of(items = orders, pageRequest = pageRequest, totalElements = totalElements)
    }
}
