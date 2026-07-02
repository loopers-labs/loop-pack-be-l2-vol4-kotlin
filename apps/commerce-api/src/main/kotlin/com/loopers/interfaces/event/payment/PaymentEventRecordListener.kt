package com.loopers.interfaces.event.payment

import com.loopers.application.event.EventRecordService
import com.loopers.domain.payment.event.PaymentEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentEventRecordListener(
    private val eventRecordService: EventRecordService,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: PaymentEvent.Requested) {
        eventRecordService.record(event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: PaymentEvent.Succeeded) {
        eventRecordService.record(event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: PaymentEvent.Failed) {
        eventRecordService.record(event)
    }
}
