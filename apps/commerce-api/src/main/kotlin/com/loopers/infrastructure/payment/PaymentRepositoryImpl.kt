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

    override fun findById(id: Long): Payment? {
        return paymentJpaRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByTransactionKey(transactionKey: String): Payment? {
        return paymentJpaRepository.findByTransactionKeyAndDeletedAtIsNull(transactionKey)
            ?.toDomain()
    }

    override fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId)
            ?.toDomain()
    }

    override fun findInProgressByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderIdAndStatusInAndDeletedAtIsNull(
            orderId = orderId,
            statuses = IN_PROGRESS_STATUSES,
        )?.toDomain()
    }

    override fun findRequestedOlderThan(threshold: ZonedDateTime): List<Payment> {
        return paymentJpaRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            status = PaymentStatus.REQUESTED,
            createdAt = threshold,
        ).map { it.toDomain() }
    }

    override fun findPendingOlderThan(threshold: ZonedDateTime): List<Payment> {
        return paymentJpaRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            status = PaymentStatus.PENDING,
            createdAt = threshold,
        ).map { it.toDomain() }
    }

    override fun markPgResultIfRequested(
        id: Long,
        transactionKey: String,
        status: PaymentStatus,
        reason: String?,
    ): Boolean {
        require(status != PaymentStatus.REQUESTED) {
            "PG 응답 상태는 REQUESTED일 수 없습니다."
        }

        return paymentJpaRepository.updatePgResultIfCurrent(
            id = id,
            transactionKey = transactionKey,
            currentStatus = PaymentStatus.REQUESTED,
            targetStatus = status,
            reason = reason,
        ) == 1
    }

    override fun markFailedIfRequested(id: Long, reason: String?): Boolean {
        return paymentJpaRepository.updateStatusById(
            id = id,
            currentStatus = PaymentStatus.REQUESTED,
            targetStatus = PaymentStatus.FAILED,
            reason = reason,
        ) == 1
    }

    override fun markSuccessIfPending(transactionKey: String, reason: String?): Boolean {
        return paymentJpaRepository.updateStatusIfCurrent(
            transactionKey = transactionKey,
            currentStatus = PaymentStatus.PENDING,
            targetStatus = PaymentStatus.SUCCESS,
            reason = reason,
        ) == 1
    }

    override fun markFailedIfPending(transactionKey: String, reason: String?): Boolean {
        return paymentJpaRepository.updateStatusIfCurrent(
            transactionKey = transactionKey,
            currentStatus = PaymentStatus.PENDING,
            targetStatus = PaymentStatus.FAILED,
            reason = reason,
        ) == 1
    }

    companion object {
        private val IN_PROGRESS_STATUSES = listOf(
            PaymentStatus.REQUESTED,
            PaymentStatus.PENDING,
        )
    }
}
