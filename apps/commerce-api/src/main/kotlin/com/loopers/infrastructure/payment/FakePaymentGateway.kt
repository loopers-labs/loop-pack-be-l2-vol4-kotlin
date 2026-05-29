package com.loopers.infrastructure.payment

import com.loopers.application.order.PaymentGateway
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class FakePaymentGateway : PaymentGateway {
    private var failNextApproval: Boolean = false
    val canceledTransactionIds: MutableList<String> = mutableListOf()

    override fun approve(command: PaymentGateway.ApproveCommand): PaymentGateway.Approval {
        if (failNextApproval) {
            failNextApproval = false
            throw CoreException(ErrorType.BAD_REQUEST, "결제 승인에 실패했습니다.")
        }
        return PaymentGateway.Approval(paymentTransactionId = "payment-${command.orderId}")
    }

    override fun cancel(paymentTransactionId: String) {
        canceledTransactionIds.add(paymentTransactionId)
    }

    fun failNextApproval() {
        failNextApproval = true
    }

    fun reset() {
        failNextApproval = false
        canceledTransactionIds.clear()
    }
}
