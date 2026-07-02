package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun save(payment: PaymentModel): PaymentModel {
        return paymentJpaRepository.save(payment)
    }

    override fun findById(id: Long): PaymentModel? {
        return paymentJpaRepository.findById(id).orElse(null)
    }

    override fun findByOrderId(orderId: Long): PaymentModel? {
        return paymentJpaRepository.findByOrderId(orderId)
    }

    override fun findByTransactionKey(transactionKey: String): PaymentModel? {
        return paymentJpaRepository.findByTransactionKey(transactionKey)
    }
}
