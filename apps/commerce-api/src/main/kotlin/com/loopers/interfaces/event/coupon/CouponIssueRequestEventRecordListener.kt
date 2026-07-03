package com.loopers.interfaces.event.coupon

import com.loopers.application.event.EventRecordService
import com.loopers.domain.coupon.event.CouponIssueRequestEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CouponIssueRequestEventRecordListener(
    private val eventRecordService: EventRecordService,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: CouponIssueRequestEvent.Requested) {
        eventRecordService.record(event)
    }
}
