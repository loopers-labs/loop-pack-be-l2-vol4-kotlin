package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
import com.loopers.application.payment.PaymentStatus
import com.loopers.application.payment.PaymentTransactionInfo
import org.springframework.stereotype.Component

@Component
class LocalPaymentGateway : PaymentGateway {
    override fun pay(command: PaymentCommand): PaymentResult {
        return PaymentResult(
            transactionKey = "local:TR:${System.currentTimeMillis()}",
            status = PaymentStatus.SUCCESS,
            reason = null,
        )
    }

    override fun getTransactionStatus(transactionKey: String): PaymentTransactionInfo {
        throw UnsupportedOperationException("LocalPaymentGateway는 상태 조회를 지원하지 않습니다.")
    }

    override fun getTransactionsByOrderId(orderId: String): List<PaymentTransactionInfo> {
        return emptyList()
    }
}
