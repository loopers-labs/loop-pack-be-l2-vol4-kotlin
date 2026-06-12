package com.loopers.application.order

import com.loopers.domain.order.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderConfirmService(
    private val orderApplicationService: OrderApplicationService,
) {
    @Transactional
    fun confirm(orderId: Long): Order {
        return orderApplicationService.markPaid(orderId)
    }
}
