package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentInitiationEventListener(
    private val orderService: OrderService,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentInitiatedEvent) {
        val order = orderService.getById(event.orderId)
        orderService.save(order.updateStatus(OrderStatus.PAYMENT_PENDING))
    }
}
