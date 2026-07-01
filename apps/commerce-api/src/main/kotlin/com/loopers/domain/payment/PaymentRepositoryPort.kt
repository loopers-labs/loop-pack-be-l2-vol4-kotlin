package com.loopers.domain.payment

interface PaymentRepositoryPort {
    fun save(payment: Payment): Payment

    fun findByTransactionKey(transactionKey: String): Payment?
}
