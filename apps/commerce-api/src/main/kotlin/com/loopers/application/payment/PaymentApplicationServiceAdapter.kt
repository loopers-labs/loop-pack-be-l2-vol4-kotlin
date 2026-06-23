package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentGatewayPort
import com.loopers.domain.payment.PaymentGatewayRequest
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.interfaces.api.payment.PaymentApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentApplicationServiceAdapter(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val paymentInitiation: PaymentInitiation,
) : PaymentApplicationServicePort {

    override fun pay(command: PayCommand): PaymentResult {
        val order = orderService.getById(command.orderId)
        if (order.userId != command.userId) {
            throw CoreException(ErrorType.FORBIDDEN, "본인의 주문만 결제할 수 있습니다.")
        }
        if (order.status != OrderStatus.CREATED) {
            throw CoreException(ErrorType.CONFLICT, "결제할 수 없는 주문 상태입니다: ${order.status}")
        }
        val amount = order.getActualAmount()

        // 외부 PG 호출은 트랜잭션 밖에서 수행한다.
        val gatewayResponse = paymentGatewayPort.requestPayment(
            PaymentGatewayRequest(
                userId = command.userId,
                orderId = order.id,
                cardType = command.cardType,
                cardNo = command.cardNo,
                amount = amount,
            ),
        )

        val payment = paymentInitiation.initiate(order, command, amount, gatewayResponse.transactionKey)
        return PaymentResult.from(payment)
    }

    @Transactional
    override fun handleCallback(command: PaymentCallbackCommand) {
        if (command.status != PaymentStatus.SUCCESS) {
            // happy path: 실패/대기 콜백은 처리하지 않는다.
            return
        }
        val payment = paymentService.getByTransactionKey(command.transactionKey)
        paymentService.save(payment.approve())

        val order = orderService.getById(payment.orderId)
        orderService.save(order.updateStatus(OrderStatus.PAYMENT_COMPLETED))
    }
}
