package com.loopers.infrastructure.payment

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findByTransactionKey(transactionKey: String): PaymentEntity?
}
