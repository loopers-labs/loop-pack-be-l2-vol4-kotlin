package com.loopers.application.payment

import com.loopers.application.payment.dto.PaymentCommand
import com.loopers.application.payment.dto.PaymentInfo
import com.loopers.application.user.UserService
import com.loopers.infrastructure.payment.client.PgPaymentCircuitOpenException
import com.loopers.infrastructure.payment.client.PgPaymentClient
import com.loopers.infrastructure.payment.client.PgPaymentCommand
import com.loopers.infrastructure.payment.client.PgPaymentRequestException
import com.loopers.infrastructure.payment.client.PgPaymentTimeoutException
import org.springframework.stereotype.Component

@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val userService: UserService,
    private val pgPaymentClient: PgPaymentClient,
) {
    fun handleCallback(command: PaymentCommand.Callback): PaymentInfo {
        return paymentService.handleCallback(command)
    }

    fun getPayment(
        loginId: String,
        rawPassword: String,
        paymentId: Long,
    ): PaymentInfo {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)

        return paymentService.getPayment(memberId = user.id, paymentId = paymentId)
    }

    fun syncPayment(
        loginId: String,
        rawPassword: String,
        paymentId: Long,
    ): PaymentInfo {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        val payment = paymentService.getPayment(memberId = user.id, paymentId = paymentId)
        val transactions = pgPaymentClient.findTransactionsByOrder(
            PgPaymentCommand.FindByOrder(
                userId = user.id.toString(),
                orderNumber = payment.orderNumber,
            ),
        )

        val transaction = transactions.firstOrNull()
            ?: return paymentService.markConfirmationNotFound(payment.paymentId)

        return paymentService.applyPgTransaction(
            paymentId = payment.paymentId,
            transactionKey = transaction.transactionKey,
            status = transaction.status,
            reason = transaction.reason,
        )
    }

    fun requestPayment(
        loginId: String,
        rawPassword: String,
        command: PaymentCommand.Request,
    ): PaymentInfo {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        val preparedPayment = paymentService.preparePayment(memberId = user.id, command = command)

        return runCatching {
            pgPaymentClient.request(
                PgPaymentCommand.Request(
                    userId = user.id.toString(),
                    orderNumber = preparedPayment.orderNumber,
                    cardType = preparedPayment.cardType,
                    cardNo = preparedPayment.cardNo,
                    amount = preparedPayment.amount,
                ),
            )
        }.fold(
            onSuccess = { result ->
                paymentService.markPending(
                    paymentId = preparedPayment.paymentId,
                    transactionKey = result.transactionKey,
                    reason = result.reason,
                )
            },
            onFailure = { e ->
                when (e) {
                    is PgPaymentTimeoutException -> paymentService.markPendingConfirmation(
                        paymentId = preparedPayment.paymentId,
                        reason = "Payment gateway response timed out. Confirmation is required.",
                    )
                    is PgPaymentCircuitOpenException -> paymentService.markRequestFailed(
                        paymentId = preparedPayment.paymentId,
                        reason = "Payment gateway circuit is open.",
                    )
                    is PgPaymentRequestException -> paymentService.markRequestFailed(
                        paymentId = preparedPayment.paymentId,
                        reason = "Payment gateway request failed.",
                    )
                    else -> paymentService.markRequestFailed(
                        paymentId = preparedPayment.paymentId,
                        reason = "Payment gateway request failed.",
                    )
                }
            },
        )
    }
}
