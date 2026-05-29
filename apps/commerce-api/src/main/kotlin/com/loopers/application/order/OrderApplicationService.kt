package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderApplicationService(
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun createOrder(userId: Long, items: List<OrderItem>): Order {
        return orderRepository.save(
            Order(
                userId = userId,
                items = items,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun getOrder(id: Long): Order {
        return orderRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다. id=$id")
    }

    @Transactional
    fun markPaid(id: Long): Order {
        val order = getOrder(id)
        order.markPaid()
        return orderRepository.save(order)
    }

    @Transactional
    fun markPaymentFailed(id: Long): Order {
        val order = getOrder(id)
        order.markPaymentFailed()
        return orderRepository.save(order)
    }
}
