package com.loopers.application.order

import com.loopers.application.coupon.CouponApplicationService
import com.loopers.application.payment.PaymentApplicationService
import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway as PaymentGatewayPort
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentStatus
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
    private val orderCatalogPort: OrderCatalogPort,
) {
    @Transactional
    fun checkout(command: OrderCommand.CheckoutRequest): OrderInfo.Detail =
        checkout(
            OrderCommand.Checkout(
                userId = command.userId,
                items = toSnapshotItems(command.items),
                deliveryAddress = command.deliveryAddress,
                deliveryRequest = command.deliveryRequest,
                phoneNumber = command.phoneNumber,
                reservationExpiresAt = command.reservationExpiresAt,
                couponId = command.couponId,
            ),
        )

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

        val payment = paymentApplicationService.getByOrderId(order.id)
        if (payment.status == PaymentStatus.READY && payment.pgTransactionId != null) {
            return orderApplicationService.getDetail(order.id)
        }
        val pgResult = paymentGateway.approve(
            PaymentCommand.Approve(
                userId = command.userId,
                orderId = order.id,
                paymentRequestId = payment.paymentRequestId,
                cardType = command.cardType,
                cardNo = command.cardNo,
                amount = payment.requestedAmount,
            ),
        )
        if (!pgResult.success || pgResult.pgTransactionId == null) {
            val reconciled = reconcileProviderTransaction(command, order, payment, pgResult)
            if (reconciled != null) {
                return reconciled
            }
            paymentApplicationService.recordApproveFailed(
                orderId = order.id,
                pgStatus = pgResult.pgStatus,
                failureReason = pgResult.failureReason ?: "PG 승인 요청에 실패했습니다.",
                rawResponseSummary = pgResult.rawResponseSummary,
            )
            throw CoreException(ErrorType.BAD_REQUEST, pgResult.failureReason ?: "결제 승인 요청에 실패했습니다.")
        }

        paymentApplicationService.recordApproveRequested(
            orderId = order.id,
            paymentKey = pgResult.pgTransactionId,
            pgTransactionId = pgResult.pgTransactionId,
        )
        return orderApplicationService.getDetail(order.id)
    }

    private fun reconcileProviderTransaction(
        command: OrderCommand.Pay,
        order: Order,
        payment: com.loopers.application.payment.PaymentInfo,
        failedResult: PaymentGatewayPort.PgResult,
    ): OrderInfo.Detail? {
        val transaction = paymentGateway.findByOrder(
            PaymentCommand.FindByOrder(
                userId = command.userId,
                orderId = order.id,
            ),
        ).firstOrNull { it.amount == payment.requestedAmount }
            ?: return null

        paymentApplicationService.recordApproveRequested(
            orderId = order.id,
            paymentKey = transaction.transactionKey,
            pgTransactionId = transaction.transactionKey,
        )

        return when (transaction.status) {
            "PENDING" -> orderApplicationService.getDetail(order.id)
            "SUCCESS" -> completeReconciledSuccess(order.id, transaction)
            "FAILED" -> {
                paymentApplicationService.recordVerifyFailed(
                    orderId = order.id,
                    pgStatus = transaction.status,
                    failureReason = transaction.failureReason ?: "PG 결제에 실패했습니다.",
                    rawResponseSummary = transaction.rawResponseSummary,
                )
                throw CoreException(ErrorType.BAD_REQUEST, transaction.failureReason ?: "결제 승인 요청에 실패했습니다.")
            }
            else -> {
                paymentApplicationService.recordApproveFailed(
                    orderId = order.id,
                    pgStatus = failedResult.pgStatus,
                    failureReason = failedResult.failureReason ?: "PG 승인 요청에 실패했습니다.",
                    rawResponseSummary = failedResult.rawResponseSummary,
                )
                null
            }
        }
    }

    private fun completeReconciledSuccess(
        orderId: Long,
        transaction: PaymentGatewayPort.PgTransaction,
    ): OrderInfo.Detail {
        val payment = paymentApplicationService.recordVerifySucceeded(
            orderId = orderId,
            pgTransactionId = transaction.transactionKey,
            approvedAmount = transaction.amount,
            pgStatus = transaction.status,
            rawResponseSummary = transaction.rawResponseSummary,
        )
        if (payment.status != PaymentStatus.APPROVED) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다.")
        }
        return runCatching {
            paymentCompletionApplicationService.completePaymentPending(orderId)
        }.getOrElse { throwable ->
            paymentCompletionApplicationService.markCompletionFailed(
                orderId,
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
        // FAILED orders may already be paid, so recovery verifies PG state before replaying internal completion.
        val pgResult = paymentGateway.verify(
            PaymentCommand.Verify(
                userId = orderApplicationService.getDetail(orderId).userId,
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
        // Provider cancellation is the point of no return; local stock/payment rollback happens only after success.
        val pgResult = paymentGateway.cancel(
            PaymentCommand.Cancel(
                userId = payment.orderId.let { orderApplicationService.getDetail(it).userId },
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

    private fun toSnapshotItems(items: List<OrderCommand.CheckoutRequestItem>): List<OrderCommand.CheckoutItem> {
        if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 품목은 비어있을 수 없습니다.")
        val products = orderCatalogPort.getOrderProducts(items.map { it.productId }).associateBy { it.productId }
        return items.map { item ->
            val product = products[item.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
            if (!product.orderable) {
                throw CoreException(ErrorType.BAD_REQUEST, "주문할 수 없는 상품입니다.")
            }
            OrderCommand.CheckoutItem(
                productId = product.productId,
                productNameSnapshot = product.productName,
                brandNameSnapshot = product.brandName,
                priceSnapshot = product.price,
                quantity = item.quantity,
            )
        }
    }
}
