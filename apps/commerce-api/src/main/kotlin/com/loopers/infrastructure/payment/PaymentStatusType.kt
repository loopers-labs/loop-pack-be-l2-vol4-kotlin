package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentStatus

enum class PaymentStatusType {
    PENDING,
    SUCCESS,
    FAILED,
    ;

    fun toDomain(): PaymentStatus = when (this) {
        PENDING -> PaymentStatus.PENDING
        SUCCESS -> PaymentStatus.SUCCESS
        FAILED -> PaymentStatus.FAILED
    }

    companion object {
        fun from(domain: PaymentStatus): PaymentStatusType = when (domain) {
            PaymentStatus.PENDING -> PENDING
            PaymentStatus.SUCCESS -> SUCCESS
            PaymentStatus.FAILED -> FAILED
        }
    }
}
