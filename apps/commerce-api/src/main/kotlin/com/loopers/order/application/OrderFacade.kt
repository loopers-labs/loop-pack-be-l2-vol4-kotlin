package com.loopers.order.application

import org.springframework.stereotype.Component

// Tx 어노테이션 금지 — PG 연동(O-F1) 시 place(Tx①) → approve(Tx 밖) → confirm/fail(Tx②) 를 지휘한다
@Component
class OrderFacade(
    private val orderService: OrderService,
) {
    fun place(userId: Long, command: OrderCreateCommand): OrderInfo =
        orderService.place(userId, command)
}
