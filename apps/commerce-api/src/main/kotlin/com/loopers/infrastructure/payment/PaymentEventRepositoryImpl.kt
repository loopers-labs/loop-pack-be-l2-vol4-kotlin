package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentEvent
import com.loopers.domain.payment.PaymentEventRepository
import org.springframework.stereotype.Component

@Component
class PaymentEventRepositoryImpl(
    private val paymentEventJpaRepository: PaymentEventJpaRepository,
) : PaymentEventRepository {
    override fun save(event: PaymentEvent): PaymentEvent = paymentEventJpaRepository.save(event)

    override fun findByOrderId(orderId: Long): List<PaymentEvent> =
        paymentEventJpaRepository.findAllByOrderIdAndDeletedAtIsNullOrderByIdAsc(orderId)
}
