package com.loopers.interfaces.event.like

import com.loopers.application.event.EventRecordService
import com.loopers.domain.like.event.ProductLikeEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductLikeRecordListener(
    private val eventRecordService: EventRecordService,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: ProductLikeEvent.Like) {
        eventRecordService.record(event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: ProductLikeEvent.Unlike) {
        eventRecordService.record(event)
    }
}
