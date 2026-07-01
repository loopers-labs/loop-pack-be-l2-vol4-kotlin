package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryAdapter(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepositoryPort {

    override fun save(payment: Payment): Payment {
        val entity = if (payment.id == 0L) {
            PaymentEntity.from(payment)
        } else {
            val existing = paymentJpaRepository.findById(payment.id)
                .orElseThrow { CoreException(ErrorType.NOT_FOUND, "결제건을 찾을 수 없습니다.") }
            existing.updateResult(PaymentStatusType.from(payment.status), payment.reason)
            existing
        }
        return paymentJpaRepository.save(entity).toDomain()
    }

    override fun findByTransactionKey(transactionKey: String): Payment? =
        paymentJpaRepository.findByTransactionKey(transactionKey)?.toDomain()
}
