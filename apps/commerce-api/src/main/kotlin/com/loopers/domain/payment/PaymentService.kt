package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class PaymentService(
    private val paymentRepositoryPort: PaymentRepositoryPort,
) {
    fun save(payment: Payment): Payment = paymentRepositoryPort.save(payment)

    fun getByTransactionKey(transactionKey: String): Payment =
        paymentRepositoryPort.findByTransactionKey(transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제건을 찾을 수 없습니다.")
}
