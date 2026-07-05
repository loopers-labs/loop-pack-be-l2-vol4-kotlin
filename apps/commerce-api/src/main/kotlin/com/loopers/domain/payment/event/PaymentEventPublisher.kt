package com.loopers.domain.payment.event

interface PaymentEventPublisher {
    fun publish(event: PaymentEvent.Requested)
    fun publish(event: PaymentEvent.Succeeded)
    fun publish(event: PaymentEvent.Failed)
}
