package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun findByTransactionKey(transactionKey: String): Payment?

    fun findByOrderId(orderId: Long): Payment?

    fun findPendingOlderThan(threshold: java.time.ZonedDateTime): List<Payment>

    fun markSuccessIfPending(transactionKey: String, reason: String?): Boolean

    fun markFailedIfPending(transactionKey: String, reason: String?): Boolean
}
