package com.loopers.interfaces.api.order

import com.loopers.application.order.CreateOrderCommand
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.order.OrderDetail
import com.loopers.domain.order.OrderSummary
import java.time.ZonedDateTime

interface OrderApplicationServicePort {
    fun createOrder(command: CreateOrderCommand): OrderDetail
    fun getMyOrders(
        userId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
        pageRequest: PageRequest,
    ): PageResult<OrderSummary>
    fun getMyOrder(userId: Long, orderId: Long): OrderDetail
}
