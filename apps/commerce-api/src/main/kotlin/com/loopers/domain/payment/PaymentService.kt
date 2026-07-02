package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    @Transactional
    fun createPayment(orderId: Long, amount: Long): PaymentModel {
        return paymentRepository.save(PaymentModel(orderId = orderId, amount = amount))
    }

    @Transactional(readOnly = true)
    fun getByOrderId(orderId: Long): PaymentModel {
        return paymentRepository.findByOrderId(orderId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "결제 정보를 찾을 수 없습니다.",
            )
    }

    @Transactional(readOnly = true)
    fun getByTransactionKey(transactionKey: String): PaymentModel {
        return paymentRepository.findByTransactionKey(transactionKey)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "결제 정보를 찾을 수 없습니다. (transactionKey=$transactionKey)",
            )
    }
}
