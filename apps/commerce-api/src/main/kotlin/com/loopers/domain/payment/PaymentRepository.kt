package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: PaymentModel): PaymentModel
    fun findById(id: Long): PaymentModel?
    fun findByOrderId(orderId: Long): PaymentModel?
    fun findByTransactionKey(transactionKey: String): PaymentModel?
}
