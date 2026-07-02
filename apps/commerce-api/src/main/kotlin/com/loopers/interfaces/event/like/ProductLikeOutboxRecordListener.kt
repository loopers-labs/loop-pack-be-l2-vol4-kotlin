package com.loopers.interfaces.event.like

import com.loopers.application.event.EventOutboxService
import com.loopers.domain.like.event.ProductLikeEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductLikeOutboxRecordListener(
    private val eventOutboxService: EventOutboxService,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: ProductLikeEvent.Like) {
        eventOutboxService.record(event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: ProductLikeEvent.Unlike) {
        eventOutboxService.record(event)
    }
}
