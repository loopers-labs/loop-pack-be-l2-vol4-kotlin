package com.loopers.application.payment

data class PaymentCompletedEvent(val paymentId: Long, val orderId: Long, val userId: Long)
