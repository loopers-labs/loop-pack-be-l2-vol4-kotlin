package com.loopers.application.order

import com.loopers.application.payment.PaymentApplicationService
import com.loopers.domain.order.OrderCancelReason
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Coordinates DB-side order/payment convergence after provider operations finish.
 *
 * Completion failures keep reservations allocated and move the order/payment to explicit retry states; expiration is only
 * for payment-pending orders that have no approved-provider possibility.
 */
@Component
class PaymentCompletionApplicationService(
    private val orderApplicationService: OrderApplicationService,
    private val stockApplicationService: StockApplicationService,
    private val paymentApplicationService: PaymentApplicationService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun completePaymentPending(orderId: Long): OrderInfo.Detail {
        stockApplicationService.confirmAndDeduct(orderId)
        return orderApplicationService.completePaymentPending(orderId)
    }

    @Transactional
    fun completeFailed(orderId: Long): OrderInfo.Detail {
        stockApplicationService.confirmAndDeduct(orderId)
        return orderApplicationService.completeFailed(orderId)
    }

    @Transactional
    fun markCompletionFailed(orderId: Long, reason: String): OrderInfo.Detail {
        orderApplicationService.markCompletionFailed(orderId)
        paymentApplicationService.markCompletionFailed(orderId, reason)
        return orderApplicationService.getDetail(orderId)
    }

    @Transactional
    fun markCompletedCancelRecoveryFailed(orderId: Long, reason: String): OrderInfo.Detail {
        orderApplicationService.markCompletedAsFailed(orderId)
        paymentApplicationService.markCompletionFailed(orderId, reason)
        return orderApplicationService.getDetail(orderId)
    }

    @Transactional
    fun expirePaymentPending(orderId: Long): OrderInfo.Detail {
        stockApplicationService.expireInProgress(orderId)
        val expired = orderApplicationService.expirePaymentPending(orderId)
        paymentApplicationService.expire(orderId)
        return expired
    }

    @Transactional
    fun cancelPaymentPending(orderId: Long): OrderInfo.Detail {
        stockApplicationService.cancelInProgress(orderId)
        val canceled = orderApplicationService.cancelPaymentPending(orderId, OrderCancelReason.USER_REQUESTED)
        paymentApplicationService.cancelReady(orderId)
        return canceled
    }

    @Transactional
    fun cancelCompletedAfterPgSuccess(orderId: Long, pgStatus: String, rawResponseSummary: String): OrderInfo.Detail {
        stockApplicationService.cancelCompletedAndRestore(orderId)
        val canceled = orderApplicationService.cancelCompleted(orderId, OrderCancelReason.USER_REQUESTED)
        paymentApplicationService.recordCancelSucceeded(orderId, pgStatus, rawResponseSummary)
        return canceled
    }

    @Transactional
    fun incrementRetryFailure(orderId: Long, reason: String): OrderInfo.Detail {
        val payment = paymentApplicationService.incrementCompletionRetryFailure(orderId, reason)
        if (payment.completionRetryCount >= 3) {
            val reservations = stockApplicationService.findInProgress(orderId)
            val productQuantities = reservations
                .groupBy { it.productId }
                .mapValues { entry -> entry.value.sumOf { it.quantity } }
            logger.error(
                "payment completion retry stopped orderId={} paymentId={} pgProvider={} pgTransactionId={} " +
                    "reservationIds={} productQuantities={} reason={} retryCount={}",
                orderId,
                payment.paymentId,
                payment.pgProvider,
                payment.pgTransactionId,
                reservations.map { it.id },
                productQuantities,
                reason,
                payment.completionRetryCount,
            )
        }
        return orderApplicationService.getDetail(orderId)
    }
}
