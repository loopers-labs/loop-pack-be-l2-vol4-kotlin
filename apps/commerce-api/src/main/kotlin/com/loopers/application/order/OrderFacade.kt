package com.loopers.application.order

import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderPrepareService: OrderPrepareService,
) {
    fun placeOrder(command: CreateOrderCommand): OrderInfo {
        val preparedOrder = orderPrepareService.prepare(command)
        return OrderInfo.from(preparedOrder)
    }
}
