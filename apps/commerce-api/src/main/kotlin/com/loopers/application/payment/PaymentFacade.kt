package com.loopers.application.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
) {
    @Transactional
    fun requestPayment(command: RequestPaymentCommand): PaymentInfo {
        val order = orderRepository.find(command.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다. id=${command.orderId}")

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

        val payment = paymentRepository.save(
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
}

data class RequestPaymentCommand(
    val orderId: Long,
    val userId: Long,
    val cardType: String,
    val cardNo: String,
)
