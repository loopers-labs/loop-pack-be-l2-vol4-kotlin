package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentApplicationService(
    private val paymentRepository: PaymentRepository,
) {
    @Transactional
    fun markSuccess(transactionKey: String, reason: String?): Payment {
        if (!paymentRepository.markSuccessIfPending(transactionKey, reason)) {
            throw CoreException(ErrorType.CONFLICT, "결제 성공 처리할 수 없는 상태입니다. transactionKey=$transactionKey")
        }
        return getPayment(transactionKey)
    }

    @Transactional
    fun markFailed(transactionKey: String, reason: String?): Payment {
        if (!paymentRepository.markFailedIfPending(transactionKey, reason)) {
            throw CoreException(ErrorType.CONFLICT, "결제 실패 처리할 수 없는 상태입니다. transactionKey=$transactionKey")
        }
        return getPayment(transactionKey)
    }

    @Transactional
    fun createPayment(payment: Payment): Payment {
        return paymentRepository.save(payment)
    }

    @Transactional(readOnly = true)
    fun validateNoPendingPayment(orderId: Long) {
        val existing = paymentRepository.findByOrderId(orderId)
        if (existing != null) {
            throw CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중인 주문입니다. orderId=$orderId")
        }
    }

    @Transactional(readOnly = true)
    fun getPayment(transactionKey: String): Payment {
        return paymentRepository.findByTransactionKey(transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다. transactionKey=$transactionKey")
    }
}
