package com.loopers.payment.infrastructure

import com.loopers.payment.domain.Payment
import com.loopers.payment.domain.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByTransactionKey(transactionKey: String): Payment?

    fun findByOrderId(orderId: Long): Payment?

    fun findAllByStatus(status: PaymentStatus): List<Payment>
}
