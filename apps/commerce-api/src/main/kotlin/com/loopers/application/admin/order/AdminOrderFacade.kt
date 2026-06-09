package com.loopers.application.admin.order

import com.loopers.application.order.OrderService
import com.loopers.application.order.dto.OrderInfo
import com.loopers.application.order.dto.OrderSummaryInfo
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminOrderFacade(
    private val orderService: OrderService,
) {
    @Transactional(readOnly = true)
    fun getOrders(page: Int, size: Int): Page<OrderSummaryInfo> {
        return orderService.getOrders(page = page, size = size)
    }

    @Transactional(readOnly = true)
    fun getOrder(orderId: Long): OrderInfo {
        return orderService.getOrder(orderId)
    }
}
