package com.loopers.domain.payment.infrastructure.persistence

import com.loopers.domain.payment.model.PaymentModel
import com.loopers.domain.payment.port.PaymentRepository
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentRecordJpaRepository: PaymentRecordJpaRepository,
) : PaymentRepository {
    override fun save(payment: PaymentModel): PaymentModel {
        val entity = if (payment.id > 0) {
            paymentRecordJpaRepository.findById(payment.id)
                .orElseGet { PaymentRecordJpaEntity.fromDomain(payment) }
                .also { it.updateFrom(payment) }
        } else {
            paymentRecordJpaRepository.findByOrderId(payment.orderId)
                ?.also { it.updateFrom(payment) }
                ?: PaymentRecordJpaEntity.fromDomain(payment)
        }
        return paymentRecordJpaRepository.saveAndFlush(entity).toDomain()
    }

    override fun findByIdOrNull(paymentId: Long): PaymentModel? =
        paymentRecordJpaRepository.findById(paymentId).map { it.toDomain() }.orElse(null)

    override fun findByOrderIdOrNull(orderId: Long): PaymentModel? =
        paymentRecordJpaRepository.findByOrderId(orderId)?.toDomain()

    override fun findByOrderIdForUpdateOrNull(orderId: Long): PaymentModel? =
        paymentRecordJpaRepository.findByOrderIdForUpdate(orderId)?.toDomain()

    override fun findByExternalTransactionKeyOrNull(externalTransactionKey: String): PaymentModel? =
        paymentRecordJpaRepository.findByExternalTransactionKey(externalTransactionKey)?.toDomain()

    override fun findByExternalTransactionKeyForUpdateOrNull(externalTransactionKey: String): PaymentModel? =
        paymentRecordJpaRepository.findByExternalTransactionKeyForUpdate(externalTransactionKey)?.toDomain()
}
