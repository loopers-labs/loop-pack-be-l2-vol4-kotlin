package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import org.springframework.stereotype.Component

@Component
class OrderService(
    private val orderRepository: OrderRepository,
) {
    fun save(order: Order): Order {
        return orderRepository.save(order)
    }
}
