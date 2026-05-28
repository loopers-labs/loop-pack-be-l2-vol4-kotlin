package com.loopers.infrastructure.payment

import com.loopers.domain.order.PaymentGateway
import com.loopers.domain.order.PaymentResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class StubPaymentGateway : PaymentGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun requestPayment(orderId: Long, amount: Long): PaymentResult {
        log.debug("StubPaymentGateway.requestPayment: orderId={}, amount={}", orderId, amount)
        return PaymentResult(success = true, message = null)
    }
}
