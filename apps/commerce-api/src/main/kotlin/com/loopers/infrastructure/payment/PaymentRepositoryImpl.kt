package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment = paymentJpaRepository.save(payment)

    override fun findByOrderId(orderId: Long): Payment? =
        paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId)

    override fun findByOrderIdForUpdate(orderId: Long): Payment? =
        paymentJpaRepository.findByOrderIdForUpdate(orderId)

    override fun findCompletionFailedForRetry(limit: Int): List<Payment> =
        paymentJpaRepository
            .findTop100ByStatusAndCompletionRetryCountLessThanAndDeletedAtIsNullOrderByUpdatedAtAsc(
                PaymentStatus.COMPLETION_FAILED,
                3,
            )
            .take(limit)
}
