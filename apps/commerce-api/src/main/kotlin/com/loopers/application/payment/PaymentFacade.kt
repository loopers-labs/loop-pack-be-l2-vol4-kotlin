package com.loopers.application.payment

import com.loopers.application.order.OrderApplicationService
import com.loopers.application.order.OrderConfirmService
import com.loopers.application.order.OrderReleaseService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val orderApplicationService: OrderApplicationService,
    private val paymentApplicationService: PaymentApplicationService,
    private val orderConfirmService: OrderConfirmService,
    private val orderReleaseService: OrderReleaseService,
    private val paymentGateway: PaymentGateway,
) {
    @Transactional
    fun requestPayment(command: RequestPaymentCommand): PaymentInfo {
        val order = orderApplicationService.getOrder(command.orderId)

        if (order.status != OrderStatus.PENDING_PAYMENT) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태의 주문만 결제 요청할 수 있습니다.")
        }

        val paymentResult = paymentGateway.pay(
            PaymentCommand(
                orderId = command.orderId,
                userId = command.userId,
                amount = order.paymentAmount,
                cardType = command.cardType,
                cardNo = command.cardNo,
                callbackUrl = "http://localhost:8080/api/v1/payments/callback",
            ),
        )

        val payment = paymentApplicationService.createPayment(
            Payment(
                orderId = command.orderId,
                userId = command.userId,
                transactionKey = paymentResult.transactionKey,
                cardType = command.cardType,
                cardNo = command.cardNo,
                amount = order.paymentAmount.amount,
                status = paymentResult.status,
                reason = paymentResult.reason,
            ),
        )

        return PaymentInfo.from(payment)
    }

    @Transactional
    fun handleCallback(command: PaymentCallbackCommand) {
        val payment = paymentApplicationService.getPayment(command.transactionKey)

        when (command.status) {
            PaymentStatus.SUCCESS -> {
                paymentApplicationService.markSuccess(command.transactionKey, command.reason)
                orderConfirmService.confirm(payment.orderId)
            }
            PaymentStatus.FAILED -> {
                paymentApplicationService.markFailed(command.transactionKey, command.reason)
                orderReleaseService.markPaymentFailed(payment.orderId)
            }
            PaymentStatus.PENDING -> {
                throw CoreException(ErrorType.BAD_REQUEST, "콜백 상태가 PENDING일 수 없습니다.")
            }
        }
    }
}

data class RequestPaymentCommand(
    val orderId: Long,
    val userId: Long,
    val cardType: String,
    val cardNo: String,
)

data class PaymentCallbackCommand(
    val transactionKey: String,
    val status: PaymentStatus,
    val reason: String?,
)
