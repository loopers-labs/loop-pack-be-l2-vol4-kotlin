package com.loopers.application.payment

import com.loopers.application.order.PaymentCompletionApplicationService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentCallbackApplicationService(
    private val paymentApplicationService: PaymentApplicationService,
    private val paymentCompletionApplicationService: PaymentCompletionApplicationService,
) {
    fun handle(command: Command) {
        val payment = paymentApplicationService.getByOrderId(command.orderId)
        if (payment.pgTransactionId != command.transactionKey) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 거래 식별자가 일치하지 않습니다.")
        }

        when (command.status) {
            "SUCCESS" -> handleSuccess(command)
            "FAILED" -> paymentApplicationService.recordVerifyFailed(
                orderId = command.orderId,
                pgStatus = command.status,
                failureReason = command.reason ?: "PG 결제에 실패했습니다.",
                rawResponseSummary = command.summary(),
            )
            "PENDING" -> Unit
            else -> throw CoreException(ErrorType.BAD_REQUEST, "알 수 없는 PG 결제 상태입니다.")
        }
    }

    private fun handleSuccess(command: Command) {
        paymentApplicationService.recordVerifySucceeded(
            orderId = command.orderId,
            pgTransactionId = command.transactionKey,
            approvedAmount = command.amount,
            pgStatus = command.status,
            rawResponseSummary = command.summary(),
        )
        runCatching {
            paymentCompletionApplicationService.completePaymentPending(command.orderId)
        }.getOrElse { throwable ->
            paymentCompletionApplicationService.markCompletionFailed(
                command.orderId,
                throwable.message ?: throwable.javaClass.simpleName,
            )
            throw throwable
        }
    }

    data class Command(
        val transactionKey: String,
        val orderId: Long,
        val amount: Long,
        val status: String,
        val reason: String?,
    ) {
        fun summary(): String =
            "pg simulator callback transactionKey=$transactionKey orderId=$orderId amount=$amount status=$status reason=$reason"
    }
}
