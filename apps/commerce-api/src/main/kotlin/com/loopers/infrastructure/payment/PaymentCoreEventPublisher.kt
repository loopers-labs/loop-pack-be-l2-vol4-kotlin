package com.loopers.infrastructure.payment

import com.loopers.domain.payment.event.PaymentEvent
import com.loopers.domain.payment.event.PaymentEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class PaymentCoreEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : PaymentEventPublisher {
    override fun publish(event: PaymentEvent.Requested) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun publish(event: PaymentEvent.Succeeded) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun publish(event: PaymentEvent.Failed) {
        applicationEventPublisher.publishEvent(event)
    }
}
