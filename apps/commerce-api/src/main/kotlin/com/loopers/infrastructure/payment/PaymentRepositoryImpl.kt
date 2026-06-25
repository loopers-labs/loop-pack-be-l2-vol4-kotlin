package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment {
        val entity = payment.id
            ?.let { paymentJpaRepository.findById(it).orElse(null) }
            ?.also { it.updateFrom(payment) }
            ?: PaymentJpaEntity.from(payment)

        return paymentJpaRepository.save(entity).toDomain()
    }

    override fun findByTransactionKey(transactionKey: String): Payment? {
        return paymentJpaRepository.findByTransactionKeyAndDeletedAtIsNull(transactionKey)
            ?.toDomain()
    }

    override fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId)
            ?.toDomain()
    }

    override fun markSuccessIfPending(transactionKey: String, reason: String?): Boolean {
        return paymentJpaRepository.updateStatusIfCurrent(
            transactionKey = transactionKey,
            currentStatus = PaymentStatus.PENDING,
            targetStatus = PaymentStatus.SUCCESS,
            reason = reason,
        ) == 1
    }

    override fun findPendingOlderThan(threshold: ZonedDateTime): List<Payment> {
        return paymentJpaRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            status = PaymentStatus.PENDING,
            createdAt = threshold,
        ).map { it.toDomain() }
    }

    override fun markFailedIfPending(transactionKey: String, reason: String?): Boolean {
        return paymentJpaRepository.updateStatusIfCurrent(
            transactionKey = transactionKey,
            currentStatus = PaymentStatus.PENDING,
            targetStatus = PaymentStatus.FAILED,
            reason = reason,
        ) == 1
    }
}
