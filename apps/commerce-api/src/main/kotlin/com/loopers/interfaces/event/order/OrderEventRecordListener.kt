package com.loopers.interfaces.event.order

import com.loopers.application.event.EventRecordService
import com.loopers.domain.order.event.OrderEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventRecordListener(
    private val eventRecordService: EventRecordService,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: OrderEvent.Created) {
        eventRecordService.record(event)
    }
}
