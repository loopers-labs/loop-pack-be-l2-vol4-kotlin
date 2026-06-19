package com.loopers.application.order

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderConfirmService(
    private val orderApplicationService: OrderApplicationService,
) {
    @Transactional
    fun confirm(orderId: Long): OrderConfirmResult {
        return orderApplicationService.markPaid(orderId)
    }
}
