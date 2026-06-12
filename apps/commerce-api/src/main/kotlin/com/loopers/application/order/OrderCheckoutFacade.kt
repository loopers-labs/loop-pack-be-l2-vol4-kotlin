package com.loopers.application.order

import com.loopers.application.coupon.CouponApplicationService
import com.loopers.application.payment.PaymentApplicationService
import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway as PaymentGatewayPort
import com.loopers.domain.order.Order
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
    private val couponApplicationService: CouponApplicationService,
    private val stockApplicationService: StockApplicationService,
    private val paymentApplicationService: PaymentApplicationService,
    private val paymentGateway: PaymentGatewayPort,
    private val paymentCompletionApplicationService: PaymentCompletionApplicationService,
) {
    @Transactional
    fun checkout(command: OrderCommand.Checkout): OrderInfo.Detail {
        val totalAmount = requestedAmount(command.items)
        val discountAmount = command.couponId
            ?.let { couponId ->
                couponApplicationService.useOwnedCoupon(
                    userId = command.userId,
                    couponId = couponId,
                    orderAmount = totalAmount,
                ).discountAmount
            } ?: 0L
        val order = orderApplicationService.createPending(command, discountAmount)
        stockApplicationService.reserveAll(order.orderId, command.items)
        paymentApplicationService.createReady(order.orderId, order.paymentAmount)
        return orderApplicationService.getDetail(order.orderId)
    }

    fun pay(command: OrderCommand.Pay): OrderInfo.Detail {
        val order = orderApplicationService.getOrder(command.orderId)
        return when (order.status) {
            OrderStatus.COMPLETED -> orderApplicationService.getDetail(order.id)
            OrderStatus.PAYMENT_PENDING -> approvePending(command, order)
            OrderStatus.FAILED -> retryFailedCompletion(order.id)
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
            paymentCompletionApplicationService.markCompletionFailed(
                order.id,
                throwable.message ?: throwable.javaClass.simpleName,
            )
            throw throwable
        }
    }

    fun cancel(command: OrderCommand.Cancel): OrderInfo.Detail {
        val detail = orderApplicationService.getDetail(command.orderId)
        return when (detail.status) {
            OrderStatus.PAYMENT_PENDING -> paymentCompletionApplicationService.cancelPaymentPending(command.orderId)
            OrderStatus.COMPLETED -> cancelCompleted(command.orderId)
            OrderStatus.FAILED,
            OrderStatus.EXPIRED,
            OrderStatus.CANCELED,
            OrderStatus.SHIPPING_STARTED,
            -> throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 주문 상태입니다.")
        }
    }

    private fun retryFailedCompletion(orderId: Long): OrderInfo.Detail {
        val payment = paymentApplicationService.recordVerifyRequested(orderId)
        val pgResult = paymentGateway.verify(
            PaymentCommand.Verify(
                orderId = orderId,
                paymentRequestId = payment.paymentRequestId,
                paymentKey = payment.paymentKey,
                pgTransactionId = payment.pgTransactionId,
                amount = payment.requestedAmount,
            ),
        )
        if (!pgResult.success || pgResult.pgTransactionId == null || pgResult.approvedAmount == null) {
            paymentApplicationService.recordVerifyFailed(
                orderId = orderId,
                pgStatus = pgResult.pgStatus,
                failureReason = pgResult.failureReason ?: "PG 검증에 실패했습니다.",
                rawResponseSummary = pgResult.rawResponseSummary,
            )
            throw CoreException(ErrorType.BAD_REQUEST, pgResult.failureReason ?: "결제 검증에 실패했습니다.")
        }
        paymentApplicationService.recordVerifySucceeded(
            orderId = orderId,
            pgTransactionId = pgResult.pgTransactionId,
            approvedAmount = pgResult.approvedAmount,
            pgStatus = pgResult.pgStatus,
            rawResponseSummary = pgResult.rawResponseSummary,
        )

        return runCatching {
            paymentCompletionApplicationService.completeFailed(orderId)
        }.getOrElse { throwable ->
            paymentCompletionApplicationService.incrementRetryFailure(
                orderId,
                throwable.message ?: throwable.javaClass.simpleName,
            )
            throw throwable
        }
    }

    private fun cancelCompleted(orderId: Long): OrderInfo.Detail {
        val payment = paymentApplicationService.recordCancelRequested(orderId)
        val pgTransactionId = payment.pgTransactionId
            ?: throw CoreException(ErrorType.CONFLICT, "PG 거래 식별자가 없는 결제는 취소할 수 없습니다.")
        val pgResult = paymentGateway.cancel(
            PaymentCommand.Cancel(
                orderId = orderId,
                pgTransactionId = pgTransactionId,
                amount = payment.approvedAmount ?: payment.requestedAmount,
            ),
        )
        if (!pgResult.success) {
            paymentApplicationService.recordCancelFailed(
                orderId = orderId,
                pgStatus = pgResult.pgStatus,
                failureReason = pgResult.failureReason ?: "PG 취소에 실패했습니다.",
                rawResponseSummary = pgResult.rawResponseSummary,
            )
            throw CoreException(ErrorType.BAD_REQUEST, pgResult.failureReason ?: "결제 취소에 실패했습니다.")
        }

        return runCatching {
            paymentCompletionApplicationService.cancelCompletedAfterPgSuccess(
                orderId,
                pgResult.pgStatus,
                pgResult.rawResponseSummary,
            )
        }.getOrElse { throwable ->
            paymentCompletionApplicationService.markCompletedCancelRecoveryFailed(
                orderId,
                throwable.message ?: throwable.javaClass.simpleName,
            )
            throw throwable
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
