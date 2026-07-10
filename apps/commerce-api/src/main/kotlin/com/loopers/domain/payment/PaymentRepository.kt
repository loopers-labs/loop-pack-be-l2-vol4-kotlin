package com.loopers.domain.payment

import com.loopers.application.payment.PaymentStatus
import java.time.ZonedDateTime

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun findById(id: Long): Payment?

    fun findByTransactionKey(transactionKey: String): Payment?

    fun findByOrderId(orderId: Long): Payment?

    fun findInProgressByOrderId(orderId: Long): Payment?

    fun findRequestedOlderThan(threshold: ZonedDateTime): List<Payment>

    fun findPendingOlderThan(threshold: ZonedDateTime): List<Payment>

    fun markPgResultIfRequested(
        id: Long,
        transactionKey: String,
        status: PaymentStatus,
        reason: String?,
    ): Boolean

    fun markFailedIfRequested(id: Long, reason: String?): Boolean

    fun markSuccessIfPending(transactionKey: String, reason: String?): Boolean

    fun markFailedIfPending(transactionKey: String, reason: String?): Boolean
}
