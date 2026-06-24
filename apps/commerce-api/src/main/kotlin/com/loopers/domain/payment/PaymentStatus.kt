package com.loopers.domain.payment

enum class PaymentStatus {
    REQUESTING,
    PENDING,
    PENDING_CONFIRMATION,
    REQUEST_FAILED,
    SUCCESS,
    FAILED,
}
