package com.loopers.order.infrastructure

import com.loopers.order.domain.Order
import com.loopers.order.domain.OrderRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun save(order: Order): Order =
        orderJpaRepository.save(order)

    override fun findById(id: Long): Order? =
        orderJpaRepository.findById(id).orElse(null)

    override fun findByUserIdAndOrderedAtBetween(userId: Long, startAt: LocalDateTime, endAt: LocalDateTime): List<Order> =
        orderJpaRepository.findByUserIdAndOrderedAtBetween(userId, startAt, endAt)
}
