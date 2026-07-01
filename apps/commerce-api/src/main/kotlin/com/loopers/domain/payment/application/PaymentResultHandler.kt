package com.loopers.domain.payment.application

import com.loopers.domain.payment.application.info.PaymentInfo
import com.loopers.domain.payment.application.service.PaymentService
import com.loopers.domain.payment.port.PaymentCompensationPort
import com.loopers.domain.payment.port.PaymentOrderPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 확정된 결제 결과(승인/실패)를 주문·재고·쿠폰까지 하나의 트랜잭션으로 반영한다.
 * 동기 요청 경로와 콜백 경로가 같은 트랜잭션 경계를 공유하도록 별도 빈으로 분리한다.
 * (외부 호출을 포함하는 PaymentFacade.request 는 트랜잭션을 가질 수 없고,
 *  자기 호출(self-invocation)로는 프록시 트랜잭션이 적용되지 않기 때문이다.)
 */
@Component
class PaymentResultHandler(
    private val paymentService: PaymentService,
    private val paymentOrderPort: PaymentOrderPort,
    private val paymentCompensationPort: PaymentCompensationPort,
) {
    @Transactional
    fun approve(transactionKey: String): PaymentInfo {
        val result = paymentService.approveByTransactionKey(transactionKey)
        if (result.changed) {
            paymentOrderPort.markOrdered(result.payment.orderId)
        }
        return PaymentInfo.from(result.payment)
    }

    @Transactional
    fun fail(transactionKey: String, reason: String?): PaymentInfo {
        val result = paymentService.failByTransactionKey(transactionKey, reason)
        if (result.changed) {
            val order = paymentOrderPort.getPendingOrder(result.payment.orderId)
            paymentOrderPort.markPaymentFailed(order.id)
            paymentCompensationPort.restore(order)
            paymentOrderPort.detachCoupon(order.id)
        }
        return PaymentInfo.from(result.payment)
    }
}
