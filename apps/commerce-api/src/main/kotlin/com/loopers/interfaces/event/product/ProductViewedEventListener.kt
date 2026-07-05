package com.loopers.interfaces.event.product

import com.loopers.application.event.EventRecordService
import com.loopers.application.event.ExternalEventSendService
import com.loopers.application.event.ProductExternalEventMessagePayload
import com.loopers.config.event.ApplicationEventAsyncConfig.Companion.EVENT_ASYNC_TASK_EXECUTOR
import com.loopers.domain.product.event.ProductEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class ProductViewedEventListener(
    private val eventRecordService: EventRecordService,
    private val sendService: ExternalEventSendService,
) {
    @Async(EVENT_ASYNC_TASK_EXECUTOR)
    @EventListener
    fun handle(event: ProductEvent.Viewed) {
        val message = ProductExternalEventMessagePayload.from(event)
        eventRecordService.record(event)
        sendService.send(message)
    }
}
