package com.loopers.payment.infrastructure

import com.loopers.payment.domain.Payment
import com.loopers.payment.domain.PaymentRepository
import com.loopers.payment.domain.PaymentStatus
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(private val paymentJpaRepository: PaymentJpaRepository) : PaymentRepository {
    override fun save(payment: Payment): Payment =
        paymentJpaRepository.save(payment)

    override fun findById(id: Long): Payment? =
        paymentJpaRepository.findById(id).orElse(null)

    override fun findByTransactionKey(transactionKey: String): Payment? =
        paymentJpaRepository.findByTransactionKey(transactionKey)

    override fun findByOrderId(orderId: Long): Payment? =
        paymentJpaRepository.findByOrderId(orderId)

    override fun findByStatus(status: PaymentStatus): List<Payment> =
        paymentJpaRepository.findAllByStatus(status)
}
