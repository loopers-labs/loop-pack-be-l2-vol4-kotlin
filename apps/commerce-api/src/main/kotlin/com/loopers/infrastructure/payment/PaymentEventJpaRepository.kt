package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentEvent
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentEventJpaRepository : JpaRepository<PaymentEvent, Long> {
    fun findAllByOrderIdAndDeletedAtIsNullOrderByIdAsc(orderId: Long): List<PaymentEvent>
}
