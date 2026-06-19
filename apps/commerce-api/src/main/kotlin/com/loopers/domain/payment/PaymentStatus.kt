package com.loopers.domain.payment

enum class PaymentStatus {
    READY,
    APPROVED,
    VERIFY_FAILED,
    COMPLETION_FAILED,
    EXPIRED,
    CANCELED,
}
