package com.loopers.interfaces.event.like

import com.loopers.application.event.ProductLikeExternalEventMessagePayload
import com.loopers.application.event.ExternalEventSendService
import com.loopers.config.event.ApplicationEventAsyncConfig.Companion.EVENT_ASYNC_TASK_EXECUTOR
import com.loopers.domain.like.event.ProductLikeEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductLikeEventMessageListener(
    private val sendService: ExternalEventSendService,
) {
    @Async(EVENT_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ProductLikeEvent.Like) {
        sendService.send(ProductLikeExternalEventMessagePayload.from(event))
    }

    @Async(EVENT_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ProductLikeEvent.Unlike) {
        sendService.send(ProductLikeExternalEventMessagePayload.from(event))
    }
}
