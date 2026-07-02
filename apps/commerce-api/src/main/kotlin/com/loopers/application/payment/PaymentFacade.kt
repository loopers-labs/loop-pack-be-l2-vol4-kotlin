package com.loopers.application.payment

import com.loopers.domain.coupon.IssuedCouponService
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgTransactionDetail
import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.product.ProductCacheManager
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val pgClient: PgClient,
    private val productService: ProductService,
    private val issuedCouponService: IssuedCouponService,
    private val productCacheManager: ProductCacheManager,
    @Value("\${pg.callback-url}") private val callbackUrl: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun requestPayment(userId: Long, orderId: Long, cardType: String, cardNo: String): PaymentInfo {
        val paymentSnapshot = readPaymentForRequest(userId, orderId)

        val pgResponse = pgClient.requestPayment(
            userId = userId,
            request = PgPaymentRequest(
                orderId = orderId.toString(),
                cardType = cardType,
                cardNo = cardNo,
                amount = paymentSnapshot.amount,
                callbackUrl = callbackUrl,
            ),
        )

        return saveTransactionKey(orderId, pgResponse.transactionKey)
    }

    @Transactional(readOnly = true)
    fun readPaymentForRequest(userId: Long, orderId: Long): PaymentInfo {
        val order = orderService.getOrderByIdAndUserId(orderId, userId)
        if (order.status != OrderStatus.PENDING) {
            throw CoreException(ErrorType.CONFLICT, "결제 가능한 주문 상태가 아닙니다. (status=${order.status})")
        }
        val payment = paymentService.getByOrderId(orderId)
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun saveTransactionKey(orderId: Long, transactionKey: String): PaymentInfo {
        val payment = paymentService.getByOrderId(orderId)
        payment.assignTransactionKey(transactionKey)
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun handleCallback(transactionKey: String, status: String, reason: String?) {
        val payment = paymentService.getByTransactionKey(transactionKey)
        val order = orderService.getOrder(payment.orderId)

        when (status) {
            "SUCCESS" -> {
                payment.markSuccess(transactionKey)
                order.markPaid()
            }
            "FAILED" -> {
                payment.markFailed(transactionKey, reason)
                order.markCancelled()
                compensateOrder(order)
            }
            else -> log.warn("알 수 없는 PG 상태: {} (transactionKey={})", status, transactionKey)
        }
    }

    fun verifyPaymentStatus(userId: Long, orderId: Long): PaymentInfo {
        val order = orderService.getOrderByIdAndUserId(orderId, userId)
        val payment = paymentService.getByOrderId(orderId)

        if (payment.status != PaymentStatus.PENDING) {
            return PaymentInfo.from(payment)
        }

        val transactionKey = payment.transactionKey
            ?: return PaymentInfo.from(payment)

        val pgDetail = pgClient.getTransactionStatus(userId, transactionKey)
        return reconcilePayment(payment, order, pgDetail)
    }

    @Transactional
    fun reconcilePayment(
        payment: PaymentModel,
        order: OrderModel,
        pgDetail: PgTransactionDetail,
    ): PaymentInfo {
        val freshPayment = paymentService.getByOrderId(payment.orderId)
        val freshOrder = orderService.getOrder(payment.orderId)

        if (freshPayment.status != PaymentStatus.PENDING) {
            return PaymentInfo.from(freshPayment)
        }

        when (pgDetail.status) {
            "SUCCESS" -> {
                freshPayment.markSuccess(pgDetail.transactionKey)
                freshOrder.markPaid()
            }
            "FAILED" -> {
                freshPayment.markFailed(pgDetail.transactionKey, pgDetail.reason)
                freshOrder.markCancelled()
                compensateOrder(freshOrder)
            }
        }
        return PaymentInfo.from(freshPayment)
    }

    @Transactional(readOnly = true)
    fun getPaymentStatus(userId: Long, orderId: Long): PaymentInfo {
        orderService.getOrderByIdAndUserId(orderId, userId)
        val payment = paymentService.getByOrderId(orderId)
        return PaymentInfo.from(payment)
    }

    private fun compensateOrder(order: OrderModel) {
        order.orderItems.forEach { item ->
            val product = productService.getProductWithLock(item.productId)
            product.increaseStock(item.quantity)
            productCacheManager.evictDetail(item.productId)
        }
        if (order.couponId != null) {
            val coupon = issuedCouponService.getById(order.couponId!!)
            coupon.restoreUsage()
        }
        log.info("주문 보상 처리 완료 (orderId={})", order.id)
    }
}
