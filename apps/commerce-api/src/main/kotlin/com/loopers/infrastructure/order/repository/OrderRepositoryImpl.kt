package com.loopers.infrastructure.order.repository

import com.loopers.domain.order.model.Order
import com.loopers.domain.order.repository.OrderRepository
import com.loopers.infrastructure.order.mapper.OrderMapper
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun findAll(page: Int, size: Int): Page<Order> {
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.desc("orderedAt"),
                Sort.Order.desc("id"),
            ),
        )

        return orderJpaRepository.findAll(pageable)
            .map(OrderMapper::toDomain)
    }

    override fun findById(orderId: Long): Order? {
        return orderJpaRepository.findWithItemsById(orderId)
            ?.let(OrderMapper::toDomain)
    }

    override fun findByIdForUpdate(orderId: Long): Order? {
        return orderJpaRepository.findByIdForUpdate(orderId)
            ?.let(OrderMapper::toDomain)
    }

    override fun findAllByMemberIdAndOrderedAtBetween(
        memberId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
    ): List<Order> {
        return orderJpaRepository.findAllByMemberIdAndOrderedAtBetweenOrderByOrderedAtDescIdDesc(
            memberId = memberId,
            startAt = startAt,
            endAt = endAt,
        ).map(OrderMapper::toDomain)
    }

    override fun findByMemberIdAndId(memberId: Long, orderId: Long): Order? {
        return orderJpaRepository.findByMemberIdAndId(memberId = memberId, orderId = orderId)
            ?.let(OrderMapper::toDomain)
    }

    override fun save(order: Order): Order {
        return orderJpaRepository.save(OrderMapper.toEntity(order))
            .let(OrderMapper::toDomain)
    }

    override fun updateStatus(order: Order): Order {
        val entity = orderJpaRepository.findByIdOrNull(order.id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")

        entity.updateStatus(order.status)
        return orderJpaRepository.save(entity)
            .let(OrderMapper::toDomain)
    }
}
