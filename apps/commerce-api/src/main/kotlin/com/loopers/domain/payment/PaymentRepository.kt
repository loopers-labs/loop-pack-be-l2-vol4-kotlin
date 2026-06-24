package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun findById(paymentId: Long): Payment?

    fun findByIdForUpdate(paymentId: Long): Payment?

    fun findByMemberIdAndId(memberId: Long, paymentId: Long): Payment?

    fun findLatestByOrderId(orderId: Long): Payment?

    fun findByTransactionKeyForUpdate(transactionKey: String): Payment?
}
