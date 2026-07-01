package com.loopers.application.payment

import com.loopers.application.event.PaymentCompletedEvent
import com.loopers.application.event.PaymentFailedEvent
import com.loopers.application.order.OrderApplicationService
import com.loopers.application.order.OrderConfirmService
import com.loopers.application.order.OrderReleaseService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val orderApplicationService: OrderApplicationService,
    private val paymentApplicationService: PaymentApplicationService,
    private val orderConfirmService: OrderConfirmService,
    private val orderReleaseService: OrderReleaseService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun requestPayment(command: RequestPaymentCommand): PaymentInfo {
        val order = orderApplicationService.getOrder(command.orderId)

        if (order.status != OrderStatus.PENDING_PAYMENT) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태의 주문만 결제 요청할 수 있습니다.")
        }

        val payment = try {
            paymentApplicationService.createPaymentAndPublishRequest(
                Payment(
                    orderId = command.orderId,
                    userId = command.userId,
                    cardType = command.cardType,
                    cardNo = command.cardNo,
                    amount = order.paymentAmount.amount,
                ),
                PAYMENT_CALLBACK_URL,
            )
        } catch (e: DataIntegrityViolationException) {
            val existing = paymentApplicationService.findByOrderId(command.orderId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다. orderId=${command.orderId}")
            return PaymentInfo.from(existing)
        }

        return PaymentInfo.from(payment)
    }

    @Transactional
    fun handleCallback(command: PaymentCallbackCommand) {
        val payment = paymentApplicationService.getPayment(command.transactionKey)

        when (command.status) {
            PaymentStatus.SUCCESS -> {
                paymentApplicationService.markSuccess(command.transactionKey, command.reason)
                orderConfirmService.confirm(payment.orderId)
                eventPublisher.publishEvent(
                    PaymentCompletedEvent(
                        userId = payment.userId,
                        orderId = payment.orderId,
                        transactionKey = command.transactionKey,
                        amount = payment.amount,
                    ),
                )
            }
            PaymentStatus.FAILED -> {
                paymentApplicationService.markFailed(command.transactionKey, command.reason)
                orderReleaseService.markPaymentFailed(payment.orderId)
                eventPublisher.publishEvent(
                    PaymentFailedEvent(
                        userId = payment.userId,
                        orderId = payment.orderId,
                        transactionKey = command.transactionKey,
                        reason = command.reason,
                    ),
                )
            }
            PaymentStatus.PENDING -> {
                throw CoreException(ErrorType.BAD_REQUEST, "콜백 상태가 PENDING일 수 없습니다.")
            }
            PaymentStatus.REQUESTED -> {
                throw CoreException(ErrorType.BAD_REQUEST, "콜백 상태가 REQUESTED일 수 없습니다.")
            }
        }
    }

    companion object {
        private const val PAYMENT_CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback"
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
