package com.loopers.domain.payment.model

import com.loopers.domain.payment.exception.InvalidPaymentException
import java.time.ZonedDateTime

data class PaymentModel(
    val id: Long = 0L,
    val orderId: Long,
    val externalTransactionKey: String? = null,
    val status: PaymentStatus,
    val failureReason: String? = null,
    val requestedAt: ZonedDateTime,
    val completedAt: ZonedDateTime? = null,
) {
    init {
        validateId(id)
        validateOrderId(orderId)
        validateCompletion(status, completedAt)
    }

    fun withId(id: Long): PaymentModel {
        validatePersistedId(id)
        return copy(id = id)
    }

    fun assignTransactionKey(externalTransactionKey: String): PaymentModel {
        validateTransactionKey(externalTransactionKey)
        if (this.externalTransactionKey == externalTransactionKey) {
            return this
        }
        if (this.externalTransactionKey != null) {
            throw InvalidPaymentException("외부 거래 키는 변경할 수 없습니다.")
        }
        if (status.isCompleted()) {
            return this
        }
        return copy(externalTransactionKey = externalTransactionKey)
    }

    fun markUnknown(reason: String?): PaymentModel {
        val nextStatus = status.markUnknown()
        if (nextStatus == status) {
            return this
        }
        return copy(status = nextStatus, failureReason = reason, completedAt = null)
    }

    fun approve(
        externalTransactionKey: String,
        completedAt: ZonedDateTime = ZonedDateTime.now(),
    ): PaymentModel {
        if (status == PaymentStatus.APPROVED) {
            return this
        }
        val nextStatus = status.approve()
        validateTransactionKey(externalTransactionKey)
        return copy(
            externalTransactionKey = externalTransactionKey,
            status = nextStatus,
            failureReason = null,
            completedAt = completedAt,
        )
    }

    fun fail(
        reason: String?,
        completedAt: ZonedDateTime = ZonedDateTime.now(),
    ): PaymentModel {
        if (status == PaymentStatus.FAILED) {
            return this
        }
        val nextStatus = status.fail()
        return copy(
            status = nextStatus,
            failureReason = reason,
            completedAt = completedAt,
        )
    }

    companion object {
        fun request(
            orderId: Long,
            requestedAt: ZonedDateTime = ZonedDateTime.now(),
        ): PaymentModel = PaymentModel(
            orderId = orderId,
            status = PaymentStatus.REQUESTED,
            requestedAt = requestedAt,
        )

        private fun validateId(id: Long) {
            if (id < 0) {
                throw InvalidPaymentException("결제 ID는 음수일 수 없습니다.")
            }
        }

        private fun validatePersistedId(id: Long) {
            if (id <= 0) {
                throw InvalidPaymentException("저장된 결제 ID는 양수여야 합니다.")
            }
        }

        private fun validateOrderId(orderId: Long) {
            if (orderId <= 0) {
                throw InvalidPaymentException("주문 ID는 양수여야 합니다.")
            }
        }

        private fun validateTransactionKey(externalTransactionKey: String) {
            if (externalTransactionKey.isBlank()) {
                throw InvalidPaymentException("외부 거래 키는 비어 있을 수 없습니다.")
            }
        }

        private fun validateCompletion(status: PaymentStatus, completedAt: ZonedDateTime?) {
            if (status.isCompleted() && completedAt == null) {
                throw InvalidPaymentException("완료 결제는 완료 시각이 필요합니다.")
            }
            if (!status.isCompleted() && completedAt != null) {
                throw InvalidPaymentException("미완료 결제는 완료 시각을 가질 수 없습니다.")
            }
        }
    }
}
