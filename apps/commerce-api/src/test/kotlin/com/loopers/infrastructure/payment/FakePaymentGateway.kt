package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

@Primary
@Component
class FakePaymentGateway : PaymentGateway {
    private var failNextApproval: Boolean = false
    private var failNextVerify: Boolean = false
    private var failNextCancel: Boolean = false
    private val orderTransactions: MutableMap<Long, MutableList<PaymentGateway.PgTransaction>> = mutableMapOf()
    val approveCalls: MutableList<Long> = mutableListOf()
    val canceledTransactionIds: MutableList<String> = mutableListOf()
    val transactionActiveDuringApprove: MutableList<Boolean> = mutableListOf()
    val transactionActiveDuringVerify: MutableList<Boolean> = mutableListOf()
    val transactionActiveDuringCancel: MutableList<Boolean> = mutableListOf()

    override fun approve(command: PaymentCommand.Approve): PaymentGateway.PgResult {
        transactionActiveDuringApprove.add(TransactionSynchronizationManager.isActualTransactionActive())
        approveCalls.add(command.orderId)
        if (failNextApproval) {
            failNextApproval = false
            return PaymentGateway.PgResult(false, "REJECTED", null, null, "결제 승인에 실패했습니다.", "fake approval rejected")
        }
        return PaymentGateway.PgResult(
            success = true,
            pgStatus = "PENDING",
            pgTransactionId = "payment-${command.orderId}",
            approvedAmount = null,
            failureReason = null,
            rawResponseSummary = "fake transaction requested",
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
            pgStatus = "SUCCESS",
            pgTransactionId = command.pgTransactionId ?: "payment-${command.orderId}",
            approvedAmount = command.amount,
            failureReason = null,
            rawResponseSummary = "fake verify approved",
        )
    }

    override fun findByOrder(command: PaymentCommand.FindByOrder): List<PaymentGateway.PgTransaction> =
        orderTransactions[command.orderId].orEmpty()

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

    fun rememberTransaction(
        orderId: Long,
        transactionKey: String = "payment-$orderId",
        status: String = "PENDING",
        amount: Long = 2000L,
        failureReason: String? = null,
    ) {
        orderTransactions.getOrPut(orderId) { mutableListOf() }
            .add(
                PaymentGateway.PgTransaction(
                    transactionKey = transactionKey,
                    status = status,
                    amount = amount,
                    failureReason = failureReason,
                    rawResponseSummary = "fake remembered transaction status=$status transactionKey=$transactionKey",
                ),
            )
    }

    fun reset() {
        failNextApproval = false
        failNextVerify = false
        failNextCancel = false
        orderTransactions.clear()
        approveCalls.clear()
        canceledTransactionIds.clear()
        transactionActiveDuringApprove.clear()
        transactionActiveDuringVerify.clear()
        transactionActiveDuringCancel.clear()
    }
}
