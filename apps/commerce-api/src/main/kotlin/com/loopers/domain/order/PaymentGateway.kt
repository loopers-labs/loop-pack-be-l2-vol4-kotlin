package com.loopers.domain.order

interface PaymentGateway {
    fun requestPayment(orderId: Long, amount: Long): PaymentResult
}
