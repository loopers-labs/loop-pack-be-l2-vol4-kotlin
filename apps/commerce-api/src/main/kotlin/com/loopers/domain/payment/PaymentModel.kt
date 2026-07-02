package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "payments")
class PaymentModel(
    orderId: Long,
    amount: Long,
) : BaseEntity() {

    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Column(name = "transaction_key")
    var transactionKey: String? = null
        protected set

    @Column(name = "amount", nullable = false)
    var amount: Long = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING
        protected set

    @Column(name = "reason")
    var reason: String? = null
        protected set

    fun assignTransactionKey(transactionKey: String) {
        this.transactionKey = transactionKey
    }

    fun markSuccess(transactionKey: String) {
        validatePending()
        this.transactionKey = transactionKey
        this.status = PaymentStatus.SUCCESS
    }

    fun markFailed(transactionKey: String, reason: String?) {
        validatePending()
        this.transactionKey = transactionKey
        this.status = PaymentStatus.FAILED
        this.reason = reason
    }

    private fun validatePending() {
        if (status != PaymentStatus.PENDING) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "이미 처리된 결제입니다. (status=$status)",
            )
        }
    }
}
