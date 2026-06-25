package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: PaymentModel): PaymentModel = paymentJpaRepository.save(payment)

    override fun findById(id: Long): PaymentModel? = paymentJpaRepository.findById(id).orElse(null)

    override fun findByOrderId(orderId: Long): PaymentModel? = paymentJpaRepository.findByOrderId(orderId)

    override fun findByOrderIdForUpdate(orderId: Long): PaymentModel? =
        paymentJpaRepository.findByOrderIdForUpdate(orderId)

    override fun findByTransactionKeyForUpdate(transactionKey: String): PaymentModel? =
        paymentJpaRepository.findByTransactionKeyForUpdate(transactionKey)

    override fun findStalePending(threshold: ZonedDateTime): List<PaymentModel> =
        paymentJpaRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold)

    @Transactional
    override fun compareAndSetStatus(
        id: Long,
        to: PaymentStatus,
        failureReason: PaymentFailureReason?,
        now: ZonedDateTime,
    ): Int = paymentJpaRepository.compareAndSetStatus(id, to, failureReason, now)
}
