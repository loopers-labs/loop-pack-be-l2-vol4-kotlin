package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class FakePaymentGateway :
    com.loopers.application.order.PaymentGateway,
    PaymentGateway {
    private var failNextApproval: Boolean = false
    private var failNextVerify: Boolean = false
    private var failNextCancel: Boolean = false
    val canceledTransactionIds: MutableList<String> = mutableListOf()
    val transactionActiveDuringApprove: MutableList<Boolean> = mutableListOf()
    val transactionActiveDuringVerify: MutableList<Boolean> = mutableListOf()
    val transactionActiveDuringCancel: MutableList<Boolean> = mutableListOf()

    override fun approve(
        command: com.loopers.application.order.PaymentGateway.ApproveCommand,
    ): com.loopers.application.order.PaymentGateway.Approval {
        if (failNextApproval) {
            failNextApproval = false
            throw CoreException(ErrorType.BAD_REQUEST, "결제 승인에 실패했습니다.")
        }
        return com.loopers.application.order.PaymentGateway.Approval(paymentTransactionId = "payment-${command.orderId}")
    }

    override fun cancel(paymentTransactionId: String) {
        canceledTransactionIds.add(paymentTransactionId)
    }

    override fun approve(command: PaymentCommand.Approve): PaymentGateway.PgResult {
        transactionActiveDuringApprove.add(TransactionSynchronizationManager.isActualTransactionActive())
        if (failNextApproval) {
            failNextApproval = false
            return PaymentGateway.PgResult(false, "REJECTED", null, null, "결제 승인에 실패했습니다.", "fake approval rejected")
        }
        return PaymentGateway.PgResult(
            success = true,
            pgStatus = "APPROVED",
            pgTransactionId = "payment-${command.orderId}",
            approvedAmount = command.amount,
            failureReason = null,
            rawResponseSummary = "fake approval approved",
        )
    }

    override fun verify(command: PaymentCommand.Verify): PaymentGateway.PgResult {
        transactionActiveDuringVerify.add(TransactionSynchronizationManager.isActualTransactionActive())
        if (failNextVerify) {
            failNextVerify = false
            return PaymentGateway.PgResult(false, "VERIFY_FAILED", null, null, "결제 검증에 실패했습니다.", "fake verify rejected")
        }
        return PaymentGateway.PgResult(
            success = true,
            pgStatus = "APPROVED",
            pgTransactionId = command.pgTransactionId ?: "payment-${command.orderId}",
            approvedAmount = command.amount,
            failureReason = null,
            rawResponseSummary = "fake verify approved",
        )
    }

    override fun cancel(command: PaymentCommand.Cancel): PaymentGateway.PgResult {
        transactionActiveDuringCancel.add(TransactionSynchronizationManager.isActualTransactionActive())
        if (failNextCancel) {
            failNextCancel = false
            return PaymentGateway.PgResult(false, "CANCEL_FAILED", null, null, "결제 취소에 실패했습니다.", "fake cancel rejected")
        }
        canceledTransactionIds.add(command.pgTransactionId)
        return PaymentGateway.PgResult(true, "CANCELED", command.pgTransactionId, command.amount, null, "fake cancel approved")
    }

    fun failNextApproval() {
        failNextApproval = true
    }

    fun failNextVerify() {
        failNextVerify = true
    }

    fun failNextCancel() {
        failNextCancel = true
    }

    fun reset() {
        failNextApproval = false
        failNextVerify = false
        failNextCancel = false
        canceledTransactionIds.clear()
        transactionActiveDuringApprove.clear()
        transactionActiveDuringVerify.clear()
        transactionActiveDuringCancel.clear()
    }
}
