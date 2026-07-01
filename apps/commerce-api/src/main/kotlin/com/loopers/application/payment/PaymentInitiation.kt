package com.loopers.application.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * PG 접수가 끝난 결제건을 영속화하고 주문을 결제대기로 전이하는 트랜잭션 단위.
 * 외부 PG 호출은 이 트랜잭션 밖(애플리케이션 서비스)에서 수행한다.
 */
@Component
class PaymentInitiation(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
) {
    @Transactional
    fun initiate(order: Order, command: PayCommand, amount: Long, transactionKey: String): Payment {
        val payment = paymentService.save(
            Payment.create(
                userId = command.userId,
                orderId = order.id,
                transactionKey = transactionKey,
                cardType = command.cardType,
                cardNo = command.cardNo,
                amount = amount,
            ),
        )
        orderService.save(order.updateStatus(OrderStatus.PAYMENT_PENDING))
        return payment
    }
}
