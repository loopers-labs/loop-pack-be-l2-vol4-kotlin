package com.loopers.domain.payment.model

import com.loopers.domain.payment.constant.PaymentErrorMessages
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
            throw InvalidPaymentException(PaymentErrorMessages.EXTERNAL_TRANSACTION_KEY_IMMUTABLE)
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
                throw InvalidPaymentException(PaymentErrorMessages.PAYMENT_ID_NEGATIVE)
            }
        }

        private fun validatePersistedId(id: Long) {
            if (id <= 0) {
                throw InvalidPaymentException(PaymentErrorMessages.STORED_PAYMENT_ID_NOT_POSITIVE)
            }
        }

        private fun validateOrderId(orderId: Long) {
            if (orderId <= 0) {
                throw InvalidPaymentException(PaymentErrorMessages.ORDER_ID_NOT_POSITIVE)
            }
        }

        private fun validateTransactionKey(externalTransactionKey: String) {
            if (externalTransactionKey.isBlank()) {
                throw InvalidPaymentException(PaymentErrorMessages.EXTERNAL_TRANSACTION_KEY_BLANK)
            }
        }

        private fun validateCompletion(status: PaymentStatus, completedAt: ZonedDateTime?) {
            if (status.isCompleted() && completedAt == null) {
                throw InvalidPaymentException(PaymentErrorMessages.COMPLETED_PAYMENT_REQUIRES_COMPLETED_AT)
            }
            if (!status.isCompleted() && completedAt != null) {
                throw InvalidPaymentException(PaymentErrorMessages.INCOMPLETE_PAYMENT_HAS_COMPLETED_AT)
            }
        }
    }
}
