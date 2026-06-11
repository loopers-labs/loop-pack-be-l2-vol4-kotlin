package com.loopers.domain.payment

interface PaymentEventRepository {
    fun save(event: PaymentEvent): PaymentEvent
    fun findByOrderId(orderId: Long): List<PaymentEvent>
}
