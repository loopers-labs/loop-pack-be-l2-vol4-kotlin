package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
import org.springframework.stereotype.Component

@Component
class LocalPaymentGateway : PaymentGateway {
    override fun pay(command: PaymentCommand): PaymentResult {
        return PaymentResult.SUCCESS
    }
}
