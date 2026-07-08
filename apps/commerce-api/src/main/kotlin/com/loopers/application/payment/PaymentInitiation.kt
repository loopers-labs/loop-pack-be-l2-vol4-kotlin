package com.loopers.application.payment

import com.loopers.domain.order.Order
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * PG 접수가 끝난 결제건을 영속화하는 트랜잭션 단위.
 * 주문 상태 전이(PAYMENT_PENDING)는 커밋 후 이벤트(PaymentInitiatedEvent)로 분리한다.
 * 외부 PG 호출은 이 트랜잭션 밖(애플리케이션 서비스)에서 수행한다.
 */
@Component
class PaymentInitiation(
    private val paymentService: PaymentService,
    private val eventPublisher: ApplicationEventPublisher,
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
        eventPublisher.publishEvent(PaymentInitiatedEvent(orderId = order.id))
        return payment
    }
}
