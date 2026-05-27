package com.loopers.application.order

import com.loopers.domain.order.OrderService
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val userService: UserService,
) {
    fun order(command: OrderCommand): OrderInfo {
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        return orderService.order(
            OrderService.OrderCommand(
                userId = user.id,
                items = command.items.map {
                    OrderService.OrderItemCommand(
                        productId = it.productId,
                        quantity = it.quantity,
                    )
                },
            ),
        ).let { OrderInfo.from(it) }
    }
}
