package com.loopers.application.order

import com.loopers.domain.order.DataPlatformClient
import com.loopers.domain.order.OrderCreatedEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventListener(
    private val dataPlatformClient: DataPlatformClient,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleCreated(event: OrderCreatedEvent) {
        dataPlatformClient.send(event)
    }
}
