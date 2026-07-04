package com.loopers.domain.payment.application

import com.loopers.domain.payment.application.command.PaymentCallbackCommand
import com.loopers.domain.payment.application.command.PaymentRequestCommand
import com.loopers.domain.payment.application.info.PaymentInfo
import com.loopers.domain.payment.application.info.PaymentRecoveryResult
import com.loopers.domain.payment.application.service.PaymentService
import com.loopers.domain.payment.port.PaymentGatewayPort
import com.loopers.domain.payment.port.PaymentGatewayRequest
import com.loopers.domain.payment.port.PaymentGatewayStatus
import com.loopers.domain.payment.port.PaymentGatewayUnknownException
import com.loopers.domain.payment.port.PaymentOrderPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val paymentOrderPort: PaymentOrderPort,
    private val paymentResultHandler: PaymentResultHandler,
    @Value("\${payment.callback-url}") private val callbackUrl: String,
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
                    callbackUrl = callbackUrl,
                ),
            )
        } catch (e: PaymentGatewayUnknownException) {
            paymentService.markUnknown(order.id, e.message)
            throw CoreException(ErrorType.BAD_GATEWAY, e.message, e)
        }

        val assigned = paymentService.assignTransactionKey(order.id, result.transactionKey)
        return when (result.status) {
            PaymentGatewayStatus.PENDING -> PaymentInfo.from(assigned)
            PaymentGatewayStatus.SUCCESS -> paymentResultHandler.approve(result.transactionKey)
            PaymentGatewayStatus.FAILED -> {
                paymentResultHandler.fail(result.transactionKey, result.reason)
                throw CoreException(ErrorType.CONFLICT, result.reason)
            }
        }
    }

    fun handleCallback(command: PaymentCallbackCommand): PaymentInfo =
        when (PaymentGatewayStatus.valueOf(command.status)) {
            PaymentGatewayStatus.PENDING -> PaymentInfo.from(paymentService.getByTransactionKey(command.transactionKey))
            PaymentGatewayStatus.SUCCESS -> paymentResultHandler.approve(command.transactionKey)
            PaymentGatewayStatus.FAILED -> paymentResultHandler.fail(command.transactionKey, command.reason)
        }

    /**
     * 콜백 미수신으로 UNKNOWN 에 고착된 결제를 PG 상태확인 API 로 재조정한다.
     * UNKNOWN 결제는 거래키가 없으므로 거래키가 아닌 주문 기준 findByOrderId 로 조회한다.
     */
    fun recoverUnknownPayments(): PaymentRecoveryResult {
        val events = paymentService.findPendingSyncEvents()
        var recovered = 0
        events.forEach { event ->
            val payment = paymentService.getById(event.aggregateId)
            if (payment.status.isCompleted()) {
                paymentService.markEventProcessed(event.eventId)
                recovered++
                return@forEach
            }
            val order = paymentOrderPort.getPendingOrder(payment.orderId)
            val result = paymentGatewayPort.findByOrderId(order.orderedUserId, payment.orderId)
                .firstOrNull { it.status != PaymentGatewayStatus.PENDING }
                ?: return@forEach
            paymentService.assignTransactionKey(payment.orderId, result.transactionKey)
            when (result.status) {
                PaymentGatewayStatus.SUCCESS -> paymentResultHandler.approve(result.transactionKey)
                PaymentGatewayStatus.FAILED -> paymentResultHandler.fail(result.transactionKey, result.reason)
                PaymentGatewayStatus.PENDING -> return@forEach
            }
            paymentService.markEventProcessed(event.eventId)
            recovered++
        }
        return PaymentRecoveryResult(scanned = events.size, recovered = recovered)
    }

    /** 기록만 남아 있던 결제 결과(APPROVED/FAILED) Outbox 이벤트를 소비 처리한다. */
    fun consumeResultEvents(): Int = paymentService.consumeResultEvents()
}
