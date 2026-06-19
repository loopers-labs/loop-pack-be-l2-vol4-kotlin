package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderItem
import org.springframework.data.jpa.repository.JpaRepository

interface OrderItemJpaRepository : JpaRepository<OrderItem, Long> {
    fun findAllByOrderId(orderId: Long): List<OrderItem>

    fun findAllByProductId(productId: Long): List<OrderItem>
}
