package com.loopers.application.payment

import com.loopers.application.payment.dto.PaymentCommand
import com.loopers.application.payment.dto.PaymentInfo
import com.loopers.application.user.UserService
import com.loopers.infrastructure.payment.PaymentRequestLockRepository
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
    private val paymentRequestLockRepository: PaymentRequestLockRepository,
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
        paymentService.findByIdempotencyKey(
            memberId = user.id,
            idempotencyKey = command.idempotencyKey,
        )?.let { return it }

        if (!paymentRequestLockRepository.acquireIdempotencyLock(user.id, command.idempotencyKey)) {
            return paymentService.findByIdempotencyKey(
                memberId = user.id,
                idempotencyKey = command.idempotencyKey,
            ) ?: throw com.loopers.support.error.CoreException(
                com.loopers.support.error.ErrorType.CONFLICT,
                "Payment request is already in progress.",
            )
        }

        try {
            paymentService.findByIdempotencyKey(
                memberId = user.id,
                idempotencyKey = command.idempotencyKey,
            )?.let { return it }

            val preparation = paymentService.preparePayment(memberId = user.id, command = command)
            if (!paymentRequestLockRepository.acquireOrderLock(command.orderId)) {
                throw com.loopers.support.error.CoreException(
                    com.loopers.support.error.ErrorType.CONFLICT,
                    "Payment is already in progress.",
                )
            }

            try {
                return runCatching {
                    requestPgPayment(
                        PgPaymentCommand.Request(
                            userId = user.id.toString(),
                            orderNumber = preparation.order.orderNumber,
                            cardType = command.cardType,
                            cardNo = command.cardNo,
                            amount = preparation.order.totalAmount,
                        ),
                    )
                }.fold(
                    onSuccess = { result ->
                        paymentService.createPendingPayment(
                            preparation = preparation,
                            command = command,
                            transactionKey = result.transactionKey,
                            reason = result.reason,
                        )
                    },
                    onFailure = { e ->
                        when (e) {
                            is PgPaymentTimeoutException -> paymentService.createSyncRequiredPayment(
                                preparation = preparation,
                                command = command,
                                reason = "Payment gateway response timed out. Synchronization is required.",
                            )
                            is PgPaymentCircuitOpenException -> paymentService.createFailedPayment(
                                preparation = preparation,
                                command = command,
                                reason = "Payment gateway circuit is open.",
                            )
                            is PgPaymentRequestException -> paymentService.createFailedPayment(
                                preparation = preparation,
                                command = command,
                                reason = "Payment gateway request failed.",
                            )
                            else -> paymentService.createFailedPayment(
                                preparation = preparation,
                                command = command,
                                reason = "Payment gateway request failed.",
                            )
                        }
                    },
                )
            } finally {
                paymentRequestLockRepository.releaseOrderLock(command.orderId)
            }
        } finally {
            paymentRequestLockRepository.releaseIdempotencyLock(user.id, command.idempotencyKey)
        }
    }

    private fun requestPgPayment(command: PgPaymentCommand.Request): com.loopers.infrastructure.payment.client.PgPaymentResult {
        var lastFailure: PgPaymentRequestException? = null

        repeat(PG_REQUEST_ATTEMPTS) {
            try {
                return pgPaymentClient.request(command)
            } catch (e: PgPaymentRequestException) {
                lastFailure = e
            }
        }

        throw lastFailure ?: PgPaymentRequestException(IllegalStateException("Payment gateway request failed."))
    }

    private companion object {
        private const val PG_REQUEST_ATTEMPTS = 2
    }
}
