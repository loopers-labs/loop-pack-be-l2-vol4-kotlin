package com.loopers.domain.payment

import java.time.ZonedDateTime

interface PaymentRepository {
    fun save(payment: PaymentModel): PaymentModel

    fun findById(id: Long): PaymentModel?

    fun findByOrderId(orderId: Long): PaymentModel?

    fun findByOrderIdForUpdate(orderId: Long): PaymentModel?

    fun findByTransactionKeyForUpdate(transactionKey: String): PaymentModel?

    fun findStalePending(threshold: ZonedDateTime): List<PaymentModel>
}
