package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
}
