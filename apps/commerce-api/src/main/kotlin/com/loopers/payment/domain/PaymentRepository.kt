package com.loopers.payment.domain

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun findById(id: Long): Payment?

    fun findByTransactionKey(transactionKey: String): Payment?

    fun findByOrderId(orderId: Long): Payment?

    fun findByStatus(status: PaymentStatus): List<Payment>
}
