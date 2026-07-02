package com.loopers.domain.payment

import java.time.ZonedDateTime

interface PaymentRepository {
    fun save(payment: PaymentModel): PaymentModel
    fun findByOrderId(orderId: Long): PaymentModel?
    fun findByTransactionKey(transactionKey: String): PaymentModel?
    fun findByStatusAndCreatedBefore(status: PaymentStatus, threshold: ZonedDateTime): List<PaymentModel>
}
