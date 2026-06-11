package com.loopers.application.order

import com.loopers.application.payment.PaymentApplicationService
import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway as PaymentGatewayPort
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class OrderCheckoutFacade(
    private val orderApplicationService: OrderApplicationService,
    private val stockApplicationService: StockApplicationService,
    private val paymentApplicationService: PaymentApplicationService,
    private val legacyPaymentGateway: PaymentGateway,
    private val paymentGateway: PaymentGatewayPort,
    private val paymentCompletionApplicationService: PaymentCompletionApplicationService,
) {
    @Transactional
    fun checkout(command: OrderCommand.Checkout): OrderInfo.Detail {
        val order = orderApplicationService.createPending(command)
        stockApplicationService.reserveAll(order.orderId, command.items)
        paymentApplicationService.createReady(order.orderId, requestedAmount(command.items))
        return orderApplicationService.getDetail(order.orderId)
    }

    fun pay(command: OrderCommand.Pay): OrderInfo.Detail {
        val order = orderApplicationService.getOrder(command.orderId)
        return when (order.status) {
            OrderStatus.COMPLETED -> orderApplicationService.getDetail(order.id)
            OrderStatus.PAYMENT_PENDING -> approvePending(command, order)
            OrderStatus.FAILED,
            OrderStatus.EXPIRED,
            OrderStatus.CANCELED,
            OrderStatus.SHIPPING_STARTED,
            -> throw CoreException(ErrorType.CONFLICT, "결제대기 주문만 결제할 수 있습니다.")
        }
    }

    private fun approvePending(command: OrderCommand.Pay, order: Order): OrderInfo.Detail {
        if (order.reservationExpiresAt.isBefore(LocalDateTime.now())) {
            paymentCompletionApplicationService.expirePaymentPending(order.id)
            throw CoreException(ErrorType.CONFLICT, "예약이 만료되었습니다.")
        }

        val requested = paymentApplicationService.recordApproveRequested(order.id, command.paymentKey)
        val pgResult = paymentGateway.approve(
            PaymentCommand.Approve(
                orderId = order.id,
                paymentRequestId = requested.paymentRequestId,
                paymentKey = command.paymentKey,
                amount = requested.requestedAmount,
            ),
        )
        if (!pgResult.success || pgResult.pgTransactionId == null || pgResult.approvedAmount == null) {
            paymentApplicationService.recordApproveFailed(
                orderId = order.id,
                pgStatus = pgResult.pgStatus,
                failureReason = pgResult.failureReason ?: "PG 승인에 실패했습니다.",
                rawResponseSummary = pgResult.rawResponseSummary,
            )
            throw CoreException(ErrorType.BAD_REQUEST, pgResult.failureReason ?: "결제 승인에 실패했습니다.")
        }

        paymentApplicationService.recordApproveSucceeded(
            orderId = order.id,
            pgTransactionId = pgResult.pgTransactionId,
            approvedAmount = pgResult.approvedAmount,
            pgStatus = pgResult.pgStatus,
            rawResponseSummary = pgResult.rawResponseSummary,
        )

        return runCatching {
            paymentCompletionApplicationService.completePaymentPending(order.id)
        }.getOrElse { throwable ->
            paymentCompletionApplicationService.markCompletionFailed(order.id, throwable.message ?: throwable.javaClass.simpleName)
            throw throwable
        }
    }

    @Transactional
    fun cancel(command: OrderCommand.Cancel): OrderInfo.Detail {
        val detail = orderApplicationService.getDetail(command.orderId)
        return when (detail.status) {
            OrderStatus.PAYMENT_PENDING -> paymentCompletionApplicationService.cancelPaymentPending(command.orderId)
            OrderStatus.COMPLETED -> {
                val paymentTransactionId = detail.paymentTransactionId
                    ?: throw CoreException(ErrorType.CONFLICT, "결제 식별자가 없는 주문은 결제 후 취소할 수 없습니다.")
                legacyPaymentGateway.cancel(paymentTransactionId)
                stockApplicationService.restoreConfirmed(command.orderId)
                orderApplicationService.cancelCompleted(command.orderId, OrderCancelReason.USER_REQUESTED)
            }
            OrderStatus.FAILED,
            OrderStatus.EXPIRED,
            OrderStatus.CANCELED,
            OrderStatus.SHIPPING_STARTED,
            -> throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 주문 상태입니다.")
        }
    }

    @Transactional
    fun startShipping(command: OrderCommand.StartShipping): OrderInfo.Detail =
        orderApplicationService.startShipping(command.orderId)

    fun expireReservations(command: OrderCommand.Expire): Int {
        return orderApplicationService.findExpiredPaymentPending(command.now).count { order ->
            paymentCompletionApplicationService.expirePaymentPending(order.id)
            true
        }
    }

    private fun requestedAmount(items: List<OrderCommand.CheckoutItem>): Long =
        items.sumOf { it.priceSnapshot * it.quantity }
}
