package com.loopers.interfaces.api.order

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.order.AdminOrderDetail
import com.loopers.domain.order.AdminOrderSummary

interface OrderAdminApplicationServicePort {
    fun getOrders(pageRequest: PageRequest): PageResult<AdminOrderSummary>
    fun getOrder(orderId: Long): AdminOrderDetail
}
