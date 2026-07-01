package com.loopers.domain.payment.model

import com.loopers.domain.payment.exception.InvalidPaymentException

enum class PaymentStatus(val code: Short) {
    REQUESTED(10),
    UNKNOWN(15),
    APPROVED(20),
    FAILED(30),
    ;

    fun markUnknown(): PaymentStatus = transitionTo(UNKNOWN)

    fun approve(): PaymentStatus = transitionTo(APPROVED)

    fun fail(): PaymentStatus = transitionTo(FAILED)

    fun isCompleted(): Boolean = this == APPROVED || this == FAILED

    private fun transitionTo(target: PaymentStatus): PaymentStatus {
        if (this == target) {
            return this
        }

        val allowed = when (this) {
            REQUESTED -> target == UNKNOWN || target == APPROVED || target == FAILED
            UNKNOWN -> target == APPROVED || target == FAILED
            APPROVED,
            FAILED,
            -> false
        }

        if (!allowed) {
            throw InvalidPaymentException("결제 상태는 $this 에서 $target 으로 전이할 수 없습니다.")
        }

        return target
    }

    companion object {
        fun fromCode(code: Short): PaymentStatus =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown payment status code: $code")
    }
}
