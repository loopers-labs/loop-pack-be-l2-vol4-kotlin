package com.loopers.domain.payment

import com.loopers.application.payment.PaymentStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Payment(
    val id: Long? = null,
    val orderId: Long,
    val userId: Long,
    transactionKey: String? = null,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    status: PaymentStatus = PaymentStatus.REQUESTED,
    reason: String? = null,
) {
    var transactionKey: String? = transactionKey
        private set

    var status: PaymentStatus = status
        private set

    var reason: String? = reason
        private set

    fun markPending(
        transactionKey: String,
        reason: String?,
    ) {
        if (status != PaymentStatus.REQUESTED) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 요청 상태에서만 대기 상태로 변경할 수 있습니다.")
        }
        this.transactionKey = transactionKey
        this.status = PaymentStatus.PENDING
        this.reason = reason
    }

    fun markSuccess(reason: String?) {
        guardPending()
        this.status = PaymentStatus.SUCCESS
        this.reason = reason
    }

    fun markFailed(reason: String?) {
        guardPending()
        this.status = PaymentStatus.FAILED
        this.reason = reason
    }

    private fun guardPending() {
        if (status != PaymentStatus.PENDING) {
            throw CoreException(ErrorType.BAD_REQUEST, "대기 상태의 결제만 상태를 변경할 수 있습니다.")
        }
    }
}
