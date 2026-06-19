package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findByOrderId(orderId: Long): Payment?
    fun findByOrderIdForUpdate(orderId: Long): Payment?
    fun findCompletionFailedForRetry(limit: Int): List<Payment>
}
