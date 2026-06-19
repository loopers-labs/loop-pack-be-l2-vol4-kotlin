package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentCancelCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
import org.springframework.stereotype.Component

@Component
class LocalPaymentGateway : PaymentGateway {
    override fun pay(command: PaymentCommand): PaymentResult {
        return PaymentResult.SUCCESS
    }

    override fun cancel(command: PaymentCancelCommand) {
        // 실제 PG 연동 전까지는 결제 취소 호출 지점만 보존한다.
    }
}
