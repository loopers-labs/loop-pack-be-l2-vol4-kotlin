package com.loopers.application.payment

import com.loopers.domain.payment.PaymentEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun logConfirmed(event: PaymentEvent.Confirmed) {
        log.info("결제 확정: orderId={}", event.orderId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun logFailed(event: PaymentEvent.Failed) {
        log.warn("결제 실패: orderId={}, reason={}", event.orderId, event.reason)
    }
}
