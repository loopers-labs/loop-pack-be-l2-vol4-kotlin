package com.loopers.application.order

import com.loopers.application.event.OrderCreatedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val orderPrepareService: OrderPrepareService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun placeOrder(command: CreateOrderCommand): OrderInfo {
        val preparedOrder = orderPrepareService.prepare(command)

        eventPublisher.publishEvent(
            OrderCreatedEvent(
                userId = preparedOrder.userId,
                orderId = preparedOrder.id!!,
                totalAmount = preparedOrder.paymentAmount.amount,
            ),
        )

        return OrderInfo.from(preparedOrder)
    }
}
