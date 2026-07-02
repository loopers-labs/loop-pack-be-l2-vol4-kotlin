package com.loopers.domain.payment.constant

enum class PaymentOutboxEventType {
    PAYMENT_STATUS_SYNC_REQUESTED,
    PAYMENT_APPROVED,
    PAYMENT_FAILED,
}
