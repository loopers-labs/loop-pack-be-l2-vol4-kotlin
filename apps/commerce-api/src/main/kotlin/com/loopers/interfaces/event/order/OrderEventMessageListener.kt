package com.loopers.interfaces.event.order

import com.loopers.application.event.ExternalEventSendService
import com.loopers.application.event.OrderExternalEventMessagePayload
import com.loopers.config.event.ApplicationEventAsyncConfig.Companion.EVENT_ASYNC_TASK_EXECUTOR
import com.loopers.domain.order.event.OrderEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventMessageListener(
    private val sendService: ExternalEventSendService,
) {
    @Async(EVENT_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: OrderEvent.Created) {
        sendService.send(OrderExternalEventMessagePayload.from(event))
    }
}
