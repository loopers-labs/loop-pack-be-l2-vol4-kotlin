package com.loopers.interfaces.event.coupon

import com.loopers.application.event.CouponIssueRequestExternalEventMessagePayload
import com.loopers.application.event.ExternalEventSendService
import com.loopers.config.event.ApplicationEventAsyncConfig.Companion.EVENT_ASYNC_TASK_EXECUTOR
import com.loopers.domain.coupon.event.CouponIssueRequestEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CouponIssueRequestEventMessageListener(
    private val sendService: ExternalEventSendService,
) {
    @Async(EVENT_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: CouponIssueRequestEvent.Requested) {
        sendService.send(CouponIssueRequestExternalEventMessagePayload.from(event))
    }
}
