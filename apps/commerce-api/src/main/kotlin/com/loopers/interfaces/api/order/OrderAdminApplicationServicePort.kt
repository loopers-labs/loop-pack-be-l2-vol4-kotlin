package com.loopers.interfaces.api.order

import com.loopers.application.order.AdminOrderDetail
import com.loopers.application.order.AdminOrderSummary
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface OrderAdminApplicationServicePort {
    fun getOrders(pageRequest: PageRequest): PageResult<AdminOrderSummary>
    fun getOrder(orderId: Long): AdminOrderDetail
}
