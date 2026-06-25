package com.loopers.domain.payment.application

import com.loopers.domain.payment.application.command.PaymentCallbackCommand
import com.loopers.domain.payment.application.command.PaymentRequestCommand
import com.loopers.domain.payment.application.info.PaymentInfo
import com.loopers.domain.payment.application.service.PaymentService
import com.loopers.domain.payment.port.PaymentCompensationPort
import com.loopers.domain.payment.port.PaymentGatewayPort
import com.loopers.domain.payment.port.PaymentGatewayRequest
import com.loopers.domain.payment.port.PaymentGatewayStatus
import com.loopers.domain.payment.port.PaymentGatewayUnknownException
import com.loopers.domain.payment.port.PaymentOrderPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val paymentOrderPort: PaymentOrderPort,
    private val paymentCompensationPort: PaymentCompensationPort,
) {
    fun request(command: PaymentRequestCommand): PaymentInfo {
        val order = paymentOrderPort.getPayableOrder(command.userId, command.orderId)
        val payment = paymentService.request(order.id)
        if (payment.status.isCompleted()) {
            return PaymentInfo.from(payment)
        }

        val result = try {
            paymentGatewayPort.request(
                PaymentGatewayRequest(
                    userId = command.userId,
                    orderId = order.id,
                    cardType = command.cardType,
                    cardNo = command.cardNo,
                    amount = order.paymentPrice.value,
                    callbackUrl = CALLBACK_URL,
                ),
            )
        } catch (e: PaymentGatewayUnknownException) {
            val unknown = paymentService.markUnknown(order.id, e.message)
            throw CoreException(ErrorType.BAD_GATEWAY, e.message, e).also {
                PaymentInfo.from(unknown)
            }
        }

        val assigned = paymentService.assignTransactionKey(order.id, result.transactionKey)
        return when (result.status) {
            PaymentGatewayStatus.PENDING -> PaymentInfo.from(assigned)
            PaymentGatewayStatus.SUCCESS -> handleApproved(result.transactionKey)
            PaymentGatewayStatus.FAILED -> {
                handleFailed(result.transactionKey, result.reason)
                throw CoreException(ErrorType.CONFLICT, result.reason)
            }
        }
    }

    @Transactional
    fun handleCallback(command: PaymentCallbackCommand): PaymentInfo =
        when (PaymentGatewayStatus.valueOf(command.status)) {
            PaymentGatewayStatus.PENDING -> PaymentInfo.from(paymentService.getByTransactionKey(command.transactionKey))
            PaymentGatewayStatus.SUCCESS -> handleApproved(command.transactionKey)
            PaymentGatewayStatus.FAILED -> handleFailed(command.transactionKey, command.reason)
        }

    private fun handleApproved(transactionKey: String): PaymentInfo {
        val result = paymentService.approveByTransactionKey(transactionKey)
        if (result.changed) {
            paymentOrderPort.markOrdered(result.payment.orderId)
        }
        return PaymentInfo.from(result.payment)
    }

    private fun handleFailed(transactionKey: String, reason: String?): PaymentInfo {
        val result = paymentService.failByTransactionKey(transactionKey, reason)
        if (result.changed) {
            val order = paymentOrderPort.getPendingOrder(result.payment.orderId)
            paymentOrderPort.markPaymentFailed(order.id)
            paymentCompensationPort.restore(order)
            paymentOrderPort.detachCoupon(order.id)
        }
        return PaymentInfo.from(result.payment)
    }

    companion object {
        private const val CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback"
    }
}
