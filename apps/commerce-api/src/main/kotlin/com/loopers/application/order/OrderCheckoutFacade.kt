package com.loopers.application.order

import com.loopers.application.payment.PaymentApplicationService
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderCommand
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
    private val paymentGateway: PaymentGateway,
) {
    @Transactional
    fun checkout(command: OrderCommand.Checkout): OrderInfo.Detail {
        val order = orderApplicationService.createPending(command)
        stockApplicationService.reserveAll(order.orderId, command.items)
        paymentApplicationService.createReady(order.orderId, requestedAmount(command.items))
        return orderApplicationService.getDetail(order.orderId)
    }

    @Transactional
    fun pay(command: OrderCommand.Pay): OrderInfo.Detail {
        val order = orderApplicationService.getOrder(command.orderId)
        when (order.status) {
            OrderStatus.COMPLETED -> return orderApplicationService.getDetail(order.id)
            OrderStatus.PAYMENT_PENDING -> Unit
            OrderStatus.FAILED,
            OrderStatus.EXPIRED,
            OrderStatus.CANCELED,
            OrderStatus.SHIPPING_STARTED,
            -> throw CoreException(ErrorType.CONFLICT, "결제대기 주문만 결제할 수 있습니다.")
        }
        if (order.reservationExpiresAt.isBefore(LocalDateTime.now())) {
            cancelExpired(order.id)
            throw CoreException(ErrorType.CONFLICT, "예약이 만료되었습니다.")
        }

        val approval = paymentGateway.approve(PaymentGateway.ApproveCommand(orderId = order.id))
        stockApplicationService.confirmAndDeduct(order.id)
        return orderApplicationService.completePayment(order.id, approval.paymentTransactionId)
    }

    @Transactional
    fun cancel(command: OrderCommand.Cancel): OrderInfo.Detail {
        val detail = orderApplicationService.getDetail(command.orderId)
        return when (detail.status) {
            OrderStatus.PAYMENT_PENDING -> {
                val activeReservationCount = stockApplicationService.countActive(command.orderId)
                val canceled = orderApplicationService.cancelPaymentPending(command.orderId, OrderCancelReason.USER_REQUESTED)
                stockApplicationService.cancelActive(command.orderId, activeReservationCount)
                canceled
            }
            OrderStatus.COMPLETED -> {
                val paymentTransactionId = detail.paymentTransactionId
                    ?: throw CoreException(ErrorType.CONFLICT, "결제 식별자가 없는 주문은 결제 후 취소할 수 없습니다.")
                paymentGateway.cancel(paymentTransactionId)
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

    @Transactional
    fun expireReservations(command: OrderCommand.Expire): Int {
        return orderApplicationService.findExpiredPaymentPending(command.now).count { order ->
            cancelExpired(order.id)
            true
        }
    }

    private fun cancelExpired(orderId: Long) {
        val activeReservationCount = stockApplicationService.countActive(orderId)
        orderApplicationService.cancelPaymentPending(orderId, OrderCancelReason.EXPIRED)
        stockApplicationService.cancelActive(orderId, activeReservationCount)
    }

    private fun requestedAmount(items: List<OrderCommand.CheckoutItem>): Long =
        items.sumOf { it.priceSnapshot * it.quantity }
}
