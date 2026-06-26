package com.loopers.infrastructure.payment.repository

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.infrastructure.payment.mapper.PaymentMapper
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment {
        if (payment.id > 0L) {
            val entity = paymentJpaRepository.findByIdOrNull(payment.id)
                ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found.")

            entity.update(payment)
            return paymentJpaRepository.save(entity)
                .let(PaymentMapper::toDomain)
        }

        return PaymentMapper.toEntity(payment)
            .let(paymentJpaRepository::save)
            .let(PaymentMapper::toDomain)
    }

    override fun findById(paymentId: Long): Payment? {
        return paymentJpaRepository.findByIdOrNull(paymentId)
            ?.let(PaymentMapper::toDomain)
    }

    override fun findByIdForUpdate(paymentId: Long): Payment? {
        return paymentJpaRepository.findByIdForUpdate(paymentId)
            ?.let(PaymentMapper::toDomain)
    }

    override fun findByMemberIdAndId(memberId: Long, paymentId: Long): Payment? {
        return paymentJpaRepository.findByMemberIdAndId(memberId = memberId, paymentId = paymentId)
            ?.let(PaymentMapper::toDomain)
    }

    override fun findByMemberIdAndIdempotencyKey(memberId: Long, idempotencyKey: String): Payment? {
        return paymentJpaRepository.findByMemberIdAndIdempotencyKey(
            memberId = memberId,
            idempotencyKey = idempotencyKey,
        )?.let(PaymentMapper::toDomain)
    }

    override fun findLatestByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findFirstByOrderIdOrderByIdDesc(orderId)
            ?.let(PaymentMapper::toDomain)
    }

    override fun findByTransactionKeyForUpdate(transactionKey: String): Payment? {
        return paymentJpaRepository.findByTransactionKeyForUpdate(transactionKey)
            ?.let(PaymentMapper::toDomain)
    }
}
