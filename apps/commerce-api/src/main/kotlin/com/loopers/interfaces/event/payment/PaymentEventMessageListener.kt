package com.loopers.interfaces.event.payment

import com.loopers.application.event.ExternalEventSendService
import com.loopers.application.event.OrderExternalEventMessagePayload
import com.loopers.config.event.ApplicationEventAsyncConfig.Companion.EVENT_ASYNC_TASK_EXECUTOR
import com.loopers.domain.payment.event.PaymentEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentEventMessageListener(
    private val sendService: ExternalEventSendService,
) {
    @Async(EVENT_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentEvent.Requested) {
        sendService.send(OrderExternalEventMessagePayload.from(event))
    }

    @Async(EVENT_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentEvent.Succeeded) {
        sendService.send(OrderExternalEventMessagePayload.from(event))
    }

    @Async(EVENT_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentEvent.Failed) {
        sendService.send(OrderExternalEventMessagePayload.from(event))
    }
}
