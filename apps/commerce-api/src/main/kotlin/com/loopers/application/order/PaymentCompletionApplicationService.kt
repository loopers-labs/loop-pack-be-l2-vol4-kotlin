package com.loopers.application.order

import com.loopers.application.payment.PaymentApplicationService
import com.loopers.domain.order.OrderCancelReason
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentCompletionApplicationService(
    private val orderApplicationService: OrderApplicationService,
    private val stockApplicationService: StockApplicationService,
    private val paymentApplicationService: PaymentApplicationService,
) {
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
        paymentApplicationService.incrementCompletionRetryFailure(orderId, reason)
        return orderApplicationService.getDetail(orderId)
    }
}
