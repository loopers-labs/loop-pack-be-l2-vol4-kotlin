package com.loopers.interfaces.event.order

import com.loopers.application.waitingqueue.WaitingQueueService
import com.loopers.domain.order.event.OrderEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEntryTokenCleanupListener(
    private val waitingQueueService: WaitingQueueService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: OrderEvent.Created) {
        waitingQueueService.deleteEntryToken(event.memberId)
    }
}
