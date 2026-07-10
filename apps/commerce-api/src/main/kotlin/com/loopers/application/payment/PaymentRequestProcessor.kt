package com.loopers.application.payment

import com.loopers.domain.order.OrderAmount
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PaymentRequestProcessor(
    private val paymentApplicationService: PaymentApplicationService,
    private val paymentGateway: PaymentGateway,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun process(paymentId: Long, callbackUrl: String) {
        val payment = paymentApplicationService.getPayment(paymentId)
        if (payment.status != PaymentStatus.REQUESTED) {
            log.info("이미 처리된 결제 요청입니다. paymentId={}, status={}", paymentId, payment.status)
            return
        }

        val result = paymentGateway.pay(
            PaymentCommand(
                orderId = payment.orderId,
                userId = payment.userId,
                amount = OrderAmount(payment.amount),
                cardType = payment.cardType,
                cardNo = payment.cardNo,
                callbackUrl = callbackUrl,
            ),
        )

        paymentApplicationService.markPgResult(
            paymentId = paymentId,
            transactionKey = result.transactionKey,
            status = result.status,
            reason = result.reason,
        )
    }
}
