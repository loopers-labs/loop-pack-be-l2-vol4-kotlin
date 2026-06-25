package com.loopers.domain.payment.port

import com.loopers.domain.payment.model.PaymentModel

interface PaymentRepository {
    fun save(payment: PaymentModel): PaymentModel
    fun findByIdOrNull(paymentId: Long): PaymentModel?
    fun findByOrderIdOrNull(orderId: Long): PaymentModel?
    fun findByOrderIdForUpdateOrNull(orderId: Long): PaymentModel?
    fun findByExternalTransactionKeyOrNull(externalTransactionKey: String): PaymentModel?
    fun findByExternalTransactionKeyForUpdateOrNull(externalTransactionKey: String): PaymentModel?
}
