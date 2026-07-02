package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: PaymentModel): PaymentModel = paymentJpaRepository.save(payment)

    override fun findByOrderId(orderId: Long): PaymentModel? = paymentJpaRepository.findByOrderId(orderId)

    override fun findByTransactionKey(transactionKey: String): PaymentModel? =
        paymentJpaRepository.findByTransactionKey(transactionKey)

    override fun findByStatusAndCreatedBefore(status: PaymentStatus, threshold: ZonedDateTime): List<PaymentModel> =
        paymentJpaRepository.findByStatusAndCreatedBefore(status, threshold)
}
